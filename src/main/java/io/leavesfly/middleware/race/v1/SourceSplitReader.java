package io.leavesfly.middleware.race.v1;

import io.leavesfly.middleware.race.v1.util.EncodeUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by yefei.yf on 2017/4/6.
 */
public class SourceSplitReader {

    private int CachePageCapa = AppConstants.DISK_PAGE_SIZE;
    private static File[] sourceFiles;

    private RandomAccessFile randomAccess;
    private File file;
    private byte[] preCache;
    private byte[] cache;
    private long size;
    private long currentSize;
    private int pageCount;

    private SourceSplitReader(RandomAccessFile randomAccess, long size, File file) {
        this.file = file;
        this.randomAccess = randomAccess;
        cache = new byte[CachePageCapa];
        this.size = size;
    }


    //构建原始文件的分片读取，用于并发读入，num表示单个文件划分数
    public static List<SourceSplitReader> buildSourceSplitReaders(int num) {

        List<SourceSplitReader> sourceSplitReaders = new ArrayList<>();
        for (File sourceFile : sourceFiles) {
            long fileSize = sourceFile.length();
            long pageStepNum = fileSize / num;
            try {
                long beginPosition = 0L;
                for (int j = 1; j < num; j++) {
                    RandomAccessFile randomAccess = new RandomAccessFile(sourceFile, "r");

                    long endPosition = beginPosition + pageStepNum;
                    randomAccess.seek(endPosition);
                    while (randomAccess.read() != EncodeUtil.LINE_CODE) {
                        endPosition++;
                    }

                    randomAccess.seek(beginPosition);
                    SourceSplitReader splitReader = new SourceSplitReader(randomAccess, endPosition - beginPosition + 1, sourceFile);
                    sourceSplitReaders.add(splitReader);

                    beginPosition = endPosition + 1;
                }

                RandomAccessFile randomAccess = new RandomAccessFile(sourceFile, "r");
                randomAccess.seek(beginPosition);
                sourceSplitReaders.add(new SourceSplitReader(randomAccess, fileSize - beginPosition, sourceFile));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sourceSplitReaders;
    }

    public static void setSourceFiles(File[] sourceFiles) {
        SourceSplitReader.sourceFiles = sourceFiles;
    }


    public List<Long> readLongValues() {
        List<Long> result = new LinkedList<>();
        if (currentSize >= size) {
            return result;
        }
        try {
            int readSize = randomAccess.read(cache);
            pageCount++;
            currentSize += readSize;
            if (currentSize <= size) {
                if (readSize == cache.length) {
                    preCache = EncodeUtil.charDigitByte2Long(preCache, cache, result);
                } else {
                    preCache = EncodeUtil.charDigitByte2Long(preCache, Arrays.copyOfRange(cache, 0, readSize), result);
                }
            } else {
                preCache = EncodeUtil.charDigitByte2Long(preCache, Arrays.copyOfRange(cache, 0,
                        (int) (readSize - (currentSize - size))), result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void close() {
        try {
            randomAccess.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
