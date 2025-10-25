package io.leavesfly.middleware.race.v1;

import io.leavesfly.middleware.race.KNLimit;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class AppConstants {

    public static int SINGLE_PARALLEL_READ_FILE_NUM = 5;

    //很关键，决定读写并行情况，越小读写并行效果越好，但是局部索引比较大 每个662k
    public static int LONG_BYTE_BUFFER_NUM = 128;//128

    public static int DISK_PAGE_SIZE = 1024 * 16;

    //分桶的文件数128最优
    public static int BUCKET_FILE_NUM = 128;

    //单个分桶文件的内部分桶数 512最优
    public static int FILE_INNER_BUCKET_NUM = 512;


    //=========================================================

    public final static int SOURCE_FILE_NUM = 10;

    public final static long MIN_VALUE_RANGE = 0L;

    public final static long INVALID_LONG_VALUE = MIN_VALUE_RANGE - 1L;

    public final static String INDEX_INFO_DIR = KNLimit.MIDDLE_DIR + "indexInfo/";
    public final static String INDEX_DATA_DIR = KNLimit.MIDDLE_DIR + "indexData/";

    public final static short LONG_BYTE = 8;


    public static int BUCKET_FILE_BIT_NUM = (int) (Math.log(BUCKET_FILE_NUM) / Math.log(2)) + 1;
    public static int FILE_INNER_BUCKET_BIT_NUM = (int) (Math.log(FILE_INNER_BUCKET_NUM) / Math.log(2)) + BUCKET_FILE_BIT_NUM;


}
