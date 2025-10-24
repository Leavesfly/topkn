package com.alibaba.middleware.race.bucket;

import com.alibaba.middleware.race.util.EncodeUtil;
import com.alibaba.middleware.race.util.SerializeUtil;
import com.alibaba.middleware.race.v1.AppConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class FileBucketWriter {

    private final AtomicLong dataFileCurrentSize = new AtomicLong(0L);

    private final IndexInfo indexInfo;
    private File indexFile;

    private BucketBuffer bucketBuffer;

    private int BUCKET_CACHE_CAPA = AppConstants.LONG_BYTE * AppConstants.LONG_BYTE_BUFFER_NUM;

    private final BucketBuffer.ElseNeedDo writeFunc = new BucketBuffer.ElseNeedDo() {
        @Override
        public void doSomething(Object... args) {
            int innerBucketNum = ((Integer) args[0]).intValue();
            InnerBucketInfo innerBucketInfo = indexInfo.getInnerBucketInfo(innerBucketNum);
            if (innerBucketInfo != null) {
                innerBucketInfo.add(dataFileCurrentSize.get(), AppConstants.LONG_BYTE_BUFFER_NUM);
            } else {
                innerBucketInfo = new InnerBucketInfo();
                innerBucketInfo.add(dataFileCurrentSize.get(), AppConstants.LONG_BYTE_BUFFER_NUM);
                indexInfo.addInnerBucketInfo(innerBucketNum, innerBucketInfo);
            }

            dataFileCurrentSize.addAndGet(BUCKET_CACHE_CAPA);
        }
    };

    private final BucketBuffer.ElseNeedDo flushFunc = new BucketBuffer.ElseNeedDo() {
        @Override
        public void doSomething(Object... args) {
            int innerBucketNum = ((Integer) args[0]).intValue();
            int addNum = ((Integer) args[1]).intValue() / AppConstants.LONG_BYTE;
            InnerBucketInfo innerBucketInfo = indexInfo.getInnerBucketInfo(innerBucketNum);
            if (innerBucketInfo != null) {
                innerBucketInfo.add(dataFileCurrentSize.get(), addNum);
            } else {
                innerBucketInfo = new InnerBucketInfo();
                innerBucketInfo.add(dataFileCurrentSize.get(), addNum);
                indexInfo.addInnerBucketInfo(innerBucketNum, innerBucketInfo);
            }
            //内存地址对齐，便于寻址
            dataFileCurrentSize.addAndGet(BUCKET_CACHE_CAPA);
        }
    };


    public FileBucketWriter(File dataFile, File indexFile) {

        indexInfo = new IndexInfo();
        this.indexFile = indexFile;
        try {
            bucketBuffer = new BucketBuffer(new FileOutputStream(dataFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * @param value
     * @param innerBucketNum
     */
    public void writeData(long value, final int innerBucketNum) {

        bucketBuffer.writeData(value, innerBucketNum, writeFunc);
    }


    /**
     * 将剩余缓存写入文件，并将索引信息写入索引文件
     */
    public void flush() {

        bucketBuffer.flush(flushFunc, writeFunc);
        //内存回收
        bucketBuffer = null;
    }


    public void indexInfo2File() {
        SerializeUtil.serialize2File(indexInfo, indexFile);
    }


    public IdRange getIdRangeIndexInfo(long thisBucketBeginIdIndex) {

        return indexInfo.buildSeqIdsIndexInfo(thisBucketBeginIdIndex);
    }
}