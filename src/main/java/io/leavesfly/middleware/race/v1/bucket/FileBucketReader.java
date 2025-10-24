package io.leavesfly.middleware.race.v1.bucket;

import io.leavesfly.middleware.race.v1.AppConstants;
import io.leavesfly.middleware.race.v1.util.EncodeUtil;
import io.leavesfly.middleware.race.v1.util.SerializeUtil;

import java.io.*;
import java.util.*;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class FileBucketReader {

    private IndexInfo indexInfo;

    private File dateFile;

    private int BUCKET_CACHE_CAPA = AppConstants.LONG_BYTE * AppConstants.LONG_BYTE_BUFFER_NUM;

    public FileBucketReader(File indexFile, File dateFile) {

        try {
            indexInfo = (IndexInfo) SerializeUtil.unserializeFromFile(indexFile);
            this.dateFile = dateFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据文件内的分桶标识获取该分桶的无序数据
     *
     * @param innerBucketNum
     * @return
     */
    public List<Long> readData(int innerBucketNum) {

        // 大概5000-1w 测试环境1/10
        List<Long> pageValues = new ArrayList<>();
        InnerBucketInfo innerBucketInfo = indexInfo.getIndexMapInfo().get(innerBucketNum);
        if (innerBucketInfo == null) {
            return pageValues;
        }
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(dateFile, "r");
            List<Long> pageIndexes = innerBucketInfo.getPageIndexes();
            for (long pageIndex : pageIndexes) {
                byte[] inBucketPage = new byte[BUCKET_CACHE_CAPA];

                randomAccessFile.seek(pageIndex);
                randomAccessFile.readFully(inBucketPage);

                for (int i = 0; i < BUCKET_CACHE_CAPA; ) {
                    long value = EncodeUtil.bytes2long(Arrays.copyOfRange(inBucketPage, i, i + AppConstants.LONG_BYTE));
                    if (value == AppConstants.INVALID_LONG_VALUE) {
                        break;
                    }
                    pageValues.add(value);
                    i += AppConstants.LONG_BYTE;
                }
            }
            randomAccessFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pageValues;
    }


    public int getInnerPageStartPos(long k, int innerBucketIndex) {
        InnerBucketInfo innerBucketInfo = indexInfo.getInnerBucketInfo(innerBucketIndex);
        return (int) (k - innerBucketInfo.getBeginIdIndexValue());
    }

}