package com.alibaba.middleware.race.v1;

import com.alibaba.middleware.race.KNLimit;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class AppConstants {

    //local vs online,影响比较大，初步估计 可能线上6-10时最佳
    public static int SINGLE_PARALLEL_READ_FILE_NUM = 5;//3,4,5,8,10

    //很关键，决定读写并行情况，越小读写并行效果越好，但是局部索引比较大 每个662k
    public static int LONG_BYTE_BUFFER_NUM = 128;//128

    public static int DISK_PAGE_SIZE = 1024 * 16;

    //分桶的文件数128最优
    public static int BUCKET_FILE_NUM = 128;

    //单个分桶文件的内部分桶数 512最优
    public static int FILE_INNER_BUCKET_NUM = 512;


    //读写队列数,最好是SOURCE_FILE_NUM的整数倍
    public static int INDEX_EXECUTE_THREAD_NUM = 14;


    //=========================================================

    public final static int SOURCE_FILE_NUM = 10;

    public final static int SINGLE_READ_QUEUE_NUM = SOURCE_FILE_NUM;

    public final static long MIN_VALUE_RANGE = 0L;

    public final static long INVALID_LONG_VALUE = MIN_VALUE_RANGE - 1L;

    public final static String INDEX_INFO_DIR = KNLimit.MIDDLE_DIR + "indexInfo/";
    public final static String INDEX_DATA_DIR = KNLimit.MIDDLE_DIR + "indexData/";

    public final static short LONG_BYTE = 8;

    //==================================================

    public static int BUCKET_FILE_BIT_NUM = (int) (Math.log(BUCKET_FILE_NUM) / Math.log(2)) + 1;
    public static int FILE_INNER_BUCKET_BIT_NUM = (int) (Math.log(FILE_INNER_BUCKET_NUM) / Math.log(2)) + BUCKET_FILE_BIT_NUM;

    /**
     * 刷新系统参数
     *
     * @param SINGLE_PARALLEL_READ_FILE_NUM1
     * @param LONG_BYTE_BUFFER_NUM1
     * @param DISK_PAGE_SIZE1
     * @param BUCKET_FILE_NUM1
     * @param FILE_INNER_BUCKET_NUM1
     */
    public static void reFlushArgs(int SINGLE_PARALLEL_READ_FILE_NUM1, int LONG_BYTE_BUFFER_NUM1,
                                   int DISK_PAGE_SIZE1, int BUCKET_FILE_NUM1, int FILE_INNER_BUCKET_NUM1) {

        SINGLE_PARALLEL_READ_FILE_NUM = SINGLE_PARALLEL_READ_FILE_NUM1;
        LONG_BYTE_BUFFER_NUM = LONG_BYTE_BUFFER_NUM1;
        DISK_PAGE_SIZE = DISK_PAGE_SIZE1;
        BUCKET_FILE_NUM = BUCKET_FILE_NUM1;
        FILE_INNER_BUCKET_NUM = FILE_INNER_BUCKET_NUM1;

        BUCKET_FILE_BIT_NUM = (int) (Math.log(BUCKET_FILE_NUM) / Math.log(2)) + 1;
        FILE_INNER_BUCKET_BIT_NUM = (int) (Math.log(FILE_INNER_BUCKET_NUM) / Math.log(2)) + BUCKET_FILE_BIT_NUM;

    }

    //索引方式可以更换，例如采用变长的差值的int来索引位置，或者在存储数据时压缩数据，
    //可能还涉及到矩阵的压缩等
    // 压缩数据到 1.5G以下，这样就可以一直保留在内存中，一次刷入到内存

}
