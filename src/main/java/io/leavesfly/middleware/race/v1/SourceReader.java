package io.leavesfly.middleware.race.v1;

import io.leavesfly.middleware.race.v1.bucket.BucketRule;
import io.leavesfly.middleware.race.v1.util.EncodeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 * Created by yefei.yf on 2017/4/6.
 */
public class SourceReader {

    private final static int CachePageCapa = AppConstants.DISK_PAGE_SIZE;
    private RandomAccessFile randomAccess;
    private byte[] cache;

    public SourceReader(File file) {
        try {
            randomAccess = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        cache = new byte[CachePageCapa];
    }


    public byte[] readBytes() {
        int readSize;
        try {
            readSize = randomAccess.read(cache);
            if (readSize < CachePageCapa && readSize > 0) {
                return Arrays.copyOfRange(cache, 0, readSize);
            } else if (readSize == CachePageCapa) {
                int count = 0;
                for (int i = CachePageCapa - 1; i > 0; i--) {
                    if (cache[i] == EncodeUtil.LINE_CODE) {
                        if (count == 0) {
                            return Arrays.copyOfRange(cache, 0, CachePageCapa);
                        } else {
                            long filePosition = randomAccess.getFilePointer();
                            filePosition -= count;
                            randomAccess.seek(filePosition);
                            return Arrays.copyOfRange(cache, 0, CachePageCapa - count);
                        }
                    }
                    count++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void main(String[] args) {
        int i = 0;
        try {
            SourceReader sourceReader = new SourceReader(new File(BucketRule.getResultFileName()));
            List<Long> longValues;

            do {

            } while (false);

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(i);
    }

}
