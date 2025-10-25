package io.leavesfly.middleware.race.v1;


import io.leavesfly.middleware.race.KNLimit;

import io.leavesfly.middleware.race.v1.bucket.*;
import io.leavesfly.middleware.race.v1.util.EncodeUtil;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * Created by yefei.yf on 2017/3/31.
 */
public class TopKN implements KNLimit {


    public static final Logger logger = Logger.getLogger(TopKN.class);

    static {

        Logger rootLogger = Logger.getRootLogger();
        rootLogger.setLevel(Level.INFO);
        try {
            rootLogger.addAppender(new FileAppender(new PatternLayout("%d{DATE} %-4r [%t] %-5p %c %x - %m%n"), KNLimit.LOG_DIR + "v1_log"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //初始化中间文件的索引目录
        File indexInfoDir = new File(AppConstants.INDEX_DIR);
        if (!indexInfoDir.exists()) {
            indexInfoDir.mkdirs();
        }

        File indexDataDir = new File(AppConstants.DATA_DIR_PATH);
        if (!indexDataDir.exists()) {
            indexDataDir.mkdirs();
        }

        File resultDataDir = new File(KNLimit.RESULT_DIR);
        if (!resultDataDir.exists()) {
            resultDataDir.mkdirs();
        }
    }

    /**
     * 构建"索引"文件的过程
     */
    public void init() {

        long time = System.currentTimeMillis();

        //全局索引文件
        final File globalIndexFile = new File(BucketRule.getGlobalIndexInfoFileName());
        if (globalIndexFile.exists()) {
            return;
        }

        //================1.文件初始化准备===================
        //数据源文件
        final File[] sourceFiles = new File[AppConstants.SOURCE_FILE_NUM];
        for (int i = 0; i < AppConstants.SOURCE_FILE_NUM; i++) {
            sourceFiles[i] = new File(KNLimit.getSourceDataFileName(i));
        }

        SourceSplitReader.setSourceFiles(sourceFiles);
        final List<SourceSplitReader> sourceSplitReaders = SourceSplitReader.buildSourceSplitReaders(
                AppConstants.SINGLE_PARALLEL_READ_FILE_NUM);

        final File[] indexDataFiles = new File[AppConstants.BUCKET_FILE_NUM];
        final File[] indexInfoFiles = new File[AppConstants.BUCKET_FILE_NUM];
        final FileBucketWriter[] fileBucketWriters = new FileBucketWriter[AppConstants.BUCKET_FILE_NUM];

        for (int i = 0; i < AppConstants.BUCKET_FILE_NUM; i++) {
            indexDataFiles[i] = new File(BucketRule.getIndexDataFileName(i));
            indexInfoFiles[i] = new File(BucketRule.getIndexInfoFileName(i));
            fileBucketWriters[i] = new FileBucketWriter(indexDataFiles[i], indexInfoFiles[i]);
        }

        //================2.多线程遍历原数据文件，构建分桶的索引文件===================

        int splitNum = sourceSplitReaders.size();
        ExecutorService executorService = Executors.newFixedThreadPool(splitNum, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });

        final CountDownLatch latchIndex = new CountDownLatch(splitNum);
        for (int i = 0; i < splitNum; i++) {
            final int finalIndex = i;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    SourceSplitReader splitReader = sourceSplitReaders.get(finalIndex);
                    List<Long> longValues;
                    do {
                        longValues = splitReader.readLongValues();
                        for (long value : longValues) {
                            int[] indexPosition = BucketRule.getShouldIndexPosition(value);
                            fileBucketWriters[indexPosition[0]].writeData(value, indexPosition[1]);
                        }
                    } while (!longValues.isEmpty());
                    splitReader.close();
                    latchIndex.countDown();
                }
            });
        }

        try {
            latchIndex.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("build-index-cost-time:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();

        //刷新索引数据到文件
        final CountDownLatch cpuCoreNumLatch = new CountDownLatch(AppConstants.BUCKET_FILE_NUM);
        for (final FileBucketWriter bucketWriter : fileBucketWriters) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    bucketWriter.flush();
                    cpuCoreNumLatch.countDown();
                }
            });
        }
        try {
            cpuCoreNumLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("fileBucketWriters-flush:" + (System.currentTimeMillis() - time));
        time = System.currentTimeMillis();

        //================3.构建全局的索引文件===================
        SeqIdsIndex seqIdsIndex = new SeqIdsIndex();
        IdRange idRangeIndexInfo = fileBucketWriters[0].getIdRangeIndexInfo(0l);
        seqIdsIndex.addSeqIdsIndexInfo(idRangeIndexInfo);

        for (int i = 1; i < AppConstants.BUCKET_FILE_NUM; i++) {
            idRangeIndexInfo = fileBucketWriters[i].getIdRangeIndexInfo(idRangeIndexInfo.getEndSeqIdValue() + 1);
            seqIdsIndex.addSeqIdsIndexInfo(idRangeIndexInfo);
        }
        seqIdsIndex.serialize2File(globalIndexFile);

        //分桶后的索引文件持久化
        final CountDownLatch cpuCoreNumLatch2 = new CountDownLatch(AppConstants.BUCKET_FILE_NUM);
        for (final FileBucketWriter bucketWriter : fileBucketWriters) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    bucketWriter.indexInfo2File();
                    cpuCoreNumLatch2.countDown();
                }
            });
        }

        try {
            cpuCoreNumLatch2.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executorService.shutdown();

        logger.info("build-global-init:" + (System.currentTimeMillis() - time));

    }


    @Override
    public void processTopKN(long k, int n) {

        long[] result = null;
        //==========1.从文件中构建全局索引结构=============
        SeqIdsIndex seqIdsIndex = SeqIdsIndex.getInstanceFromFile(new File(BucketRule.getGlobalIndexInfoFileName()));

        //==========2.根据全局索引定位数据分片=============
        List<int[]> bucketPositions = BucketRule.getKNIndexPosition(k, n, seqIdsIndex);
        int size = bucketPositions.size();

        //只有两个值，1或者2
        if (size == 1) {
            int[] pos = bucketPositions.get(0);
            File indexInfoFile = new File(BucketRule.getIndexInfoFileName(pos[0]));
            File indexDataFile = new File(BucketRule.getIndexDataFileName(pos[0]));
            FileBucketReader bucketReader = new FileBucketReader(indexInfoFile, indexDataFile);

            //比赛环境数据量在5000左右,并排序
            List<Long> pageValues = bucketReader.readData(pos[1]);
            long[] pageLongValues = EncodeUtil.convert(pageValues);

            Arrays.sort(pageLongValues);
            //定位排序之后的下标开始位置
            int start = bucketReader.getInnerPageStartPos(k, pos[1]);
            result = Arrays.copyOfRange(pageLongValues, start, start + n);

        } else if (size == 2) {
            int[] pos0 = bucketPositions.get(0);
            int[] pos1 = bucketPositions.get(1);

            //来自同一个索引文件的不同分区
            if (pos0[0] == pos1[0]) {
                File indexInfoFile = new File(BucketRule.getIndexInfoFileName(pos0[0]));
                File indexDataFile = new File(BucketRule.getIndexDataFileName(pos0[0]));
                final FileBucketReader bucketReader = new FileBucketReader(indexInfoFile, indexDataFile);

                final int[] bucketPos = new int[2];
                bucketPos[0] = pos0[1];
                bucketPos[1] = pos1[1];
                final long[][] resNeedMerge = new long[2][];

                final CountDownLatch latch = new CountDownLatch(2);
                for (int i = 0; i < 2; i++) {
                    final int finalIndex = i;
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<Long> pageValues = bucketReader.readData(bucketPos[finalIndex]);
                            long[] pageLongValues = EncodeUtil.convert(pageValues);
                            Arrays.sort(pageLongValues);
                            resNeedMerge[finalIndex] = pageLongValues;
                            latch.countDown();
                        }
                    });
                    thread.start();
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int start = bucketReader.getInnerPageStartPos(k, bucketPos[0]);
                int end = resNeedMerge[0].length;
                long[] resultPart1 = Arrays.copyOfRange(resNeedMerge[0], start, end);
                int left = n - (end - start);
                long[] resultPart2 = Arrays.copyOfRange(resNeedMerge[1], 0, left);

                result = EncodeUtil.merge(resultPart1, resultPart2);

            } else {  //来自不同索引文件
                final FileBucketReader[] bucketReaders = new FileBucketReader[2];

                final int[] filePos = new int[2];
                filePos[0] = pos0[0];
                filePos[1] = pos1[0];

                final int[] bucketPos = new int[2];
                bucketPos[0] = pos0[1];
                bucketPos[1] = pos1[1];

                final long[][] resNeedMerge = new long[2][];

                final CountDownLatch latch = new CountDownLatch(2);
                for (int i = 0; i < 2; i++) {
                    final int finalIndex = i;
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {

                            File indexInfoFile = new File(BucketRule.getIndexInfoFileName(filePos[finalIndex]));
                            File indexDataFile = new File(BucketRule.getIndexDataFileName(filePos[finalIndex]));
                            FileBucketReader bucketReader = new FileBucketReader(indexInfoFile, indexDataFile);
                            bucketReaders[finalIndex] = bucketReader;

                            List<Long> pageValues = bucketReader.readData(bucketPos[finalIndex]);
                            long[] pageLongValues = EncodeUtil.convert(pageValues);
                            Arrays.sort(pageLongValues);
                            resNeedMerge[finalIndex] = pageLongValues;

                            latch.countDown();
                        }
                    });
                    thread.start();
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int start = bucketReaders[0].getInnerPageStartPos(k, bucketPos[0]);
                int end = resNeedMerge[0].length;
                long[] resultPart1 = Arrays.copyOfRange(resNeedMerge[0], start, end);
                int left = n - (end - start);
                long[] resultPart2 = Arrays.copyOfRange(resNeedMerge[1], 0, left);

                result = EncodeUtil.merge(resultPart1, resultPart2);
            }
        }

        //==========3.输出结果=============
        outputResults(result);
    }

    static void outputResults(long[] resultList) {
        try {
            FileWriter fileWriter = new FileWriter(BucketRule.getResultFileName());
            for (long value : resultList) {
                fileWriter.append(String.valueOf(value)).append("\n");
            }
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        TopKN topKN = new TopKN();

        long time = System.currentTimeMillis();

        topKN.init();

        logger.info("index build-total-cost-time：" + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();

        topKN.processTopKN(10000000, 100);

        logger.info("search data-cost-time:" + (System.currentTimeMillis() - time));
    }

}
