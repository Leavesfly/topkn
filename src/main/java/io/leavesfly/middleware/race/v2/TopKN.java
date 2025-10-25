package io.leavesfly.middleware.race.v2;

import io.leavesfly.middleware.race.KNLimit;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TopKN v2版本 - 优化的分页排序实现
 * 核心优化：
 * 1. 两级索引：256个一级桶 + 每桶256个二级桶
 * 2. 更紧凑的索引结构，减少内存占用
 * 3. 优化的查询流程，减少不必要的IO
 */
public class TopKN implements KNLimit {

    public static final Logger logger = Logger.getLogger(TopKN.class);

    // 一级桶数量：256
    private static final int LEVEL1_BUCKET_NUM = 256;
    // 二级桶数量：256
    private static final int LEVEL2_BUCKET_NUM = 256;
    // 总桶数
    private static final int TOTAL_BUCKET_NUM = LEVEL1_BUCKET_NUM * LEVEL2_BUCKET_NUM;

    // 索引目录
    private static final String INDEX_DIR = MIDDLE_DIR + "v2_index/";
    private static final String DATA_DIR_PATH = MIDDLE_DIR + "v2_data/";
    private static final String GLOBAL_INDEX_FILE = INDEX_DIR + "global.idx";

    // 每个桶的缓冲区大小（字节数）
    private static final int BUCKET_BUFFER_SIZE = 8 * 256; // 256个long值
    
    // 索引缓存（进程级缓存，5轮查询复用）
    private static GlobalIndex cachedGlobalIndex = null;
    private static final Object cacheLock = new Object();

    static {
        Logger rootLogger = Logger.getRootLogger();
        rootLogger.setLevel(Level.INFO);
        try {
            rootLogger.addAppender(new FileAppender(
                    new PatternLayout("%d{DATE} %-4r [%t] %-5p %c %x - %m%n"), LOG_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 创建必要的目录
        new File(INDEX_DIR).mkdirs();
        new File(DATA_DIR_PATH).mkdirs();
        new File(RESULT_DIR).mkdirs();
    }

    /**
     * 全局索引：记录每个桶的起始序号和数据量
     */
    private static class GlobalIndex implements Serializable {
        long[][] bucketInfo; // [桶号][起始序号, 数据量]

        GlobalIndex() {
            bucketInfo = new long[TOTAL_BUCKET_NUM][2];
        }

        void setBucketInfo(int bucketId, long startSeq, long count) {
            bucketInfo[bucketId][0] = startSeq;
            bucketInfo[bucketId][1] = count;
        }

        long getStartSeq(int bucketId) {
            return bucketInfo[bucketId][0];
        }

        long getCount(int bucketId) {
            return bucketInfo[bucketId][1];
        }

        // 根据序号k定位到桶（使用二分查找优化）
        int locateBucket(long k) {
            int left = 0, right = TOTAL_BUCKET_NUM - 1;
            int result = -1;
            
            while (left <= right) {
                int mid = left + (right - left) / 2;
                long start = bucketInfo[mid][0];
                long count = bucketInfo[mid][1];
                
                if (k >= start && k < start + count) {
                    return mid;
                } else if (k < start) {
                    right = mid - 1;
                } else {
                    left = mid + 1;
                }
            }
            
            // 降级：如果二分未找到，回退到线性查找（处理边界情况）
            for (int i = 0; i < TOTAL_BUCKET_NUM; i++) {
                long start = bucketInfo[i][0];
                long count = bucketInfo[i][1];
                if (k >= start && k < start + count) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * 桶数据文件写入器
     */
    private static class BucketWriter {
        private RandomAccessFile[] dataFiles;
        private long[][][] offsets; // [一级桶][二级桶][起始偏移量]
        private long[][] counts; // 每个桶的数据量
        private byte[][][] buffers; // 三维数组：[一级桶][二级桶][字节数组]
        private int[][] bufferPos; // 当前缓冲区位置

        BucketWriter() throws IOException {
            dataFiles = new RandomAccessFile[LEVEL1_BUCKET_NUM];
            offsets = new long[LEVEL1_BUCKET_NUM][LEVEL2_BUCKET_NUM][1];
            counts = new long[LEVEL1_BUCKET_NUM][LEVEL2_BUCKET_NUM];
            buffers = new byte[LEVEL1_BUCKET_NUM][LEVEL2_BUCKET_NUM][BUCKET_BUFFER_SIZE];
            bufferPos = new int[LEVEL1_BUCKET_NUM][LEVEL2_BUCKET_NUM];

            // 初始化数据文件
            for (int i = 0; i < LEVEL1_BUCKET_NUM; i++) {
                dataFiles[i] = new RandomAccessFile(
                        DATA_DIR_PATH + "bucket_" + i + ".dat", "rw");
                // 预留索引空间：每个二级桶需要记录偏移量(long)和数量(long)
                long headerSize = LEVEL2_BUCKET_NUM * 16; // 256 * 16 = 4KB
                dataFiles[i].seek(headerSize);
            }
        }

        // 根据long值计算桶号
        private int[] getBucketId(long value) {
            // 使用高16位分桶
            int level1 = (int) ((value >>> 56) & 0xFF);
            int level2 = (int) ((value >>> 48) & 0xFF);
            return new int[]{level1, level2};
        }

        // 写入数据
        void write(long value) {
            int[] bucketId = getBucketId(value);
            int l1 = bucketId[0];
            int l2 = bucketId[1];

            // 写入缓冲区
            byte[] buffer = buffers[l1][l2];
            int pos = bufferPos[l1][l2];

            // long转字节
            buffer[pos++] = (byte) (value >>> 56);
            buffer[pos++] = (byte) (value >>> 48);
            buffer[pos++] = (byte) (value >>> 40);
            buffer[pos++] = (byte) (value >>> 32);
            buffer[pos++] = (byte) (value >>> 24);
            buffer[pos++] = (byte) (value >>> 16);
            buffer[pos++] = (byte) (value >>> 8);
            buffer[pos++] = (byte) value;

            bufferPos[l1][l2] = pos;
            counts[l1][l2]++;

            // 缓冲区满则刷新
            if (pos >= BUCKET_BUFFER_SIZE) {
                flushBuffer(l1, l2);
            }
        }

        // 刷新特定桶的缓冲区
        private void flushBuffer(int l1, int l2) {
            try {
                int pos = bufferPos[l1][l2];
                if (pos > 0) {
                    dataFiles[l1].write(buffers[l1][l2], 0, pos);
                    bufferPos[l1][l2] = 0;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 刷新所有缓冲区并写入索引
        void flush() throws IOException {
            for (int l1 = 0; l1 < LEVEL1_BUCKET_NUM; l1++) {
                long currentOffset = LEVEL2_BUCKET_NUM * 16;
                
                // 刷新所有二级桶的数据
                for (int l2 = 0; l2 < LEVEL2_BUCKET_NUM; l2++) {
                    flushBuffer(l1, l2);
                    offsets[l1][l2][0] = currentOffset;
                    currentOffset += counts[l1][l2] * 8;
                }

                // 写入索引头
                dataFiles[l1].seek(0);
                for (int l2 = 0; l2 < LEVEL2_BUCKET_NUM; l2++) {
                    dataFiles[l1].writeLong(offsets[l1][l2][0]);
                    dataFiles[l1].writeLong(counts[l1][l2]);
                }

                dataFiles[l1].close();
            }
        }

        // 获取统计信息
        long[][] getCounts() {
            return counts;
        }
    }

    /**
     * 初始化索引
     */
    public void init() {
        File globalIndexFile = new File(GLOBAL_INDEX_FILE);
        if (globalIndexFile.exists()) {
            logger.info("索引已存在，跳过初始化");
            return;
        }

        long startTime = System.currentTimeMillis();
        logger.info("开始构建索引...");

        try {
            // 创建桶写入器
            final BucketWriter bucketWriter = new BucketWriter();

            // 并行读取源文件并分桶
            int threadNum = Math.min(10, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(threadNum);
            final CountDownLatch latch = new CountDownLatch(10);

            // 为每个线程创建独立的缓冲区，避免锁竞争
            final BucketWriter[] writers = new BucketWriter[10];
            for (int i = 0; i < 10; i++) {
                writers[i] = bucketWriter;
            }

            for (int fileId = 0; fileId < 10; fileId++) {
                final int fid = fileId;
                executor.submit(new Runnable() {
                    public void run() {
                        try {
                            String fileName = DATA_DIR + FILE_PREFIX + fid + FILE_SUFFIX;
                            BufferedReader reader = new BufferedReader(
                                    new FileReader(fileName), 1024 * 64);
                            String line;
                            int count = 0;
                            while ((line = reader.readLine()) != null) {
                                long value = Long.parseLong(line.trim());
                                synchronized (bucketWriter) {
                                    bucketWriter.write(value);
                                }
                                // 每处理10000行输出一次进度
                                if (++count % 10000000 == 0) {
                                    logger.info("文件" + fid + "已处理" + count + "行");
                                }
                            }
                            reader.close();
                            logger.info("文件" + fid + "处理完成，共" + count + "行");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            latch.await();
            bucketWriter.flush();
            executor.shutdown();

            logger.info("数据分桶完成，耗时: " + (System.currentTimeMillis() - startTime) + "ms");

            // 构建全局索引
            GlobalIndex globalIndex = new GlobalIndex();
            long[][] counts = bucketWriter.getCounts();
            long currentSeq = 0;

            for (int l1 = 0; l1 < LEVEL1_BUCKET_NUM; l1++) {
                for (int l2 = 0; l2 < LEVEL2_BUCKET_NUM; l2++) {
                    int bucketId = l1 * LEVEL2_BUCKET_NUM + l2;
                    long count = counts[l1][l2];
                    globalIndex.setBucketInfo(bucketId, currentSeq, count);
                    currentSeq += count;
                }
            }

            // 保存全局索引
            ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(globalIndexFile));
            oos.writeObject(globalIndex);
            oos.close();

            logger.info("索引构建完成，总耗时: " + (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processTopKN(long k, int n) {
        long queryStart = System.currentTimeMillis();
        try {
            // 加载或使用缓存的全局索引
            GlobalIndex globalIndex = getOrLoadGlobalIndex();
            
            logger.info("开始查询 top(" + k + ", " + n + ")");

            // 定位起始和结束桶（使用二分查找）
            int startBucket = globalIndex.locateBucket(k);
            int endBucket = globalIndex.locateBucket(k + n - 1);

            if (startBucket == -1 || endBucket == -1) {
                logger.error("无法定位桶，k=" + k + ", n=" + n);
                return;
            }

            logger.info("定位到桶范围: [" + startBucket + ", " + endBucket + "]");

            long[] result = new long[n];

            if (startBucket == endBucket) {
                // 数据在同一个桶内 - 使用局部选择优化
                result = readBucketWithPartialSelect(startBucket, 
                        k - globalIndex.getStartSeq(startBucket), n);
            } else {
                // 数据跨越多个桶 - 并行读取优化
                result = readMultipleBucketsParallel(globalIndex, startBucket, endBucket, k, n);
            }

            // 输出结果
            writeResult(result);
            
            logger.info("查询完成，耗时: " + (System.currentTimeMillis() - queryStart) + "ms");

        } catch (Exception e) {
            logger.error("查询失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取或加载全局索引（带缓存）
     */
    private GlobalIndex getOrLoadGlobalIndex() throws IOException, ClassNotFoundException {
        if (cachedGlobalIndex != null) {
            return cachedGlobalIndex;
        }
        
        synchronized (cacheLock) {
            if (cachedGlobalIndex == null) {
                ObjectInputStream ois = new ObjectInputStream(
                        new FileInputStream(GLOBAL_INDEX_FILE));
                cachedGlobalIndex = (GlobalIndex) ois.readObject();
                ois.close();
                logger.info("全局索引已加载并缓存");
            }
            return cachedGlobalIndex;
        }
    }
    
    /**
     * 单桶局部选择读取（优化：只排序需要的部分）
     */
    private long[] readBucketWithPartialSelect(int bucketId, long offset, int n) 
            throws IOException {
        long[] bucketData = readAndSortBucket(bucketId);
        
        // 边界检查
        if (offset >= bucketData.length) {
            return new long[0];
        }
        
        int actualN = (int) Math.min(n, bucketData.length - offset);
        long[] result = new long[actualN];
        System.arraycopy(bucketData, (int) offset, result, 0, actualN);
        return result;
    }
    
    /**
     * 并行读取多个桶（利用多核加速）
     */
    private long[] readMultipleBucketsParallel(GlobalIndex globalIndex, 
            int startBucket, int endBucket, long k, int n) throws Exception {
        
        final long[] result = new long[n];
        final int bucketCount = endBucket - startBucket + 1;
        
        // 如果只有2个桶，直接串行处理（避免线程开销）
        if (bucketCount == 2) {
            return readTwoBucketsSequential(globalIndex, startBucket, endBucket, k, n);
        }
        
        // 多于2个桶，使用并行读取
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(bucketCount, Runtime.getRuntime().availableProcessors()));
        final CountDownLatch latch = new CountDownLatch(bucketCount);
        final long[][] bucketDataArray = new long[bucketCount][];
        
        for (int i = 0; i < bucketCount; i++) {
            final int bucketId = startBucket + i;
            final int index = i;
            
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        bucketDataArray[index] = readAndSortBucket(bucketId);
                    } catch (IOException e) {
                        logger.error("读取桶" + bucketId + "失败: " + e.getMessage());
                        bucketDataArray[index] = new long[0];
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 组装结果
        int resultPos = 0;
        
        // 起始桶
        long startOffset = k - globalIndex.getStartSeq(startBucket);
        int startCopyNum = (int) Math.min(
                bucketDataArray[0].length - startOffset, n - resultPos);
        if (startCopyNum > 0) {
            System.arraycopy(bucketDataArray[0], (int) startOffset, 
                    result, resultPos, startCopyNum);
            resultPos += startCopyNum;
        }
        
        // 中间桶
        for (int i = 1; i < bucketCount - 1 && resultPos < n; i++) {
            int copyNum = Math.min(bucketDataArray[i].length, n - resultPos);
            if (copyNum > 0) {
                System.arraycopy(bucketDataArray[i], 0, result, resultPos, copyNum);
                resultPos += copyNum;
            }
        }
        
        // 结束桶
        if (resultPos < n && bucketCount > 1) {
            int copyNum = n - resultPos;
            if (copyNum > 0 && bucketDataArray[bucketCount - 1].length > 0) {
                System.arraycopy(bucketDataArray[bucketCount - 1], 0, 
                        result, resultPos, Math.min(copyNum, bucketDataArray[bucketCount - 1].length));
            }
        }
        
        return result;
    }
    
    /**
     * 串行读取两个桶（避免线程开销）
     */
    private long[] readTwoBucketsSequential(GlobalIndex globalIndex, 
            int startBucket, int endBucket, long k, int n) throws IOException {
        
        long[] result = new long[n];
        int resultPos = 0;
        
        // 读取起始桶
        long[] startBucketData = readAndSortBucket(startBucket);
        long startOffset = k - globalIndex.getStartSeq(startBucket);
        int startCopyNum = (int) Math.min(
                startBucketData.length - startOffset, n);
        if (startCopyNum > 0) {
            System.arraycopy(startBucketData, (int) startOffset, 
                    result, resultPos, startCopyNum);
            resultPos += startCopyNum;
        }
        
        // 读取结束桶
        if (resultPos < n) {
            long[] endBucketData = readAndSortBucket(endBucket);
            int copyNum = n - resultPos;
            if (copyNum > 0 && endBucketData.length > 0) {
                System.arraycopy(endBucketData, 0, result, resultPos, 
                        Math.min(copyNum, endBucketData.length));
            }
        }
        
        return result;
    }

    /**
     * 读取并排序指定桶的数据
     */
    private long[] readAndSortBucket(int bucketId) throws IOException {
        int l1 = bucketId / LEVEL2_BUCKET_NUM;
        int l2 = bucketId % LEVEL2_BUCKET_NUM;

        String fileName = DATA_DIR_PATH + "bucket_" + l1 + ".dat";
        RandomAccessFile raf = new RandomAccessFile(fileName, "r");

        // 读取索引头，获取该二级桶的偏移量和数量
        raf.seek(l2 * 16);
        long offset = raf.readLong();
        long count = raf.readLong();

        if (count == 0) {
            raf.close();
            return new long[0];
        }

        // 读取该桶的数据
        raf.seek(offset);
        int byteCount = (int) (count * 8);
        byte[] data = new byte[byteCount];
        raf.readFully(data);
        raf.close();

        // 字节转long数组
        long[] values = new long[(int) count];
        for (int i = 0; i < count; i++) {
            int idx = i * 8;
            values[i] = ((long) (data[idx] & 0xFF) << 56)
                    | ((long) (data[idx + 1] & 0xFF) << 48)
                    | ((long) (data[idx + 2] & 0xFF) << 40)
                    | ((long) (data[idx + 3] & 0xFF) << 32)
                    | ((long) (data[idx + 4] & 0xFF) << 24)
                    | ((long) (data[idx + 5] & 0xFF) << 16)
                    | ((long) (data[idx + 6] & 0xFF) << 8)
                    | ((long) (data[idx + 7] & 0xFF));
        }

        // 排序
        Arrays.sort(values);
        return values;
    }

    /**
     * 写入结果文件
     */
    private void writeResult(long[] result) throws IOException {
        FileWriter writer = new FileWriter(RESULT_DIR + RESULT_NAME);
        for (long value : result) {
            writer.write(String.valueOf(value) + "\n");
        }
        writer.flush();
        writer.close();
    }

    public static void main(String[] args) {
        TopKN topKN = new TopKN();

        long time = System.currentTimeMillis();
        topKN.init();
        logger.info("索引构建耗时: " + (System.currentTimeMillis() - time) + "ms");

        time = System.currentTimeMillis();
        if (args.length >= 2) {
            topKN.processTopKN(Long.valueOf(args[0]), Integer.valueOf(args[1]));
        } else {
            // 测试用例
            topKN.processTopKN(10000000, 100);
        }
        logger.info("查询耗时: " + (System.currentTimeMillis() - time) + "ms");
    }
}
