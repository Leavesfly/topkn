package com.alibaba.middleware.race.bucket;

import com.alibaba.middleware.race.KNLimit;
import com.alibaba.middleware.race.util.SearchUtil;
import com.alibaba.middleware.race.v1.AppConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by yefei.yf on 2017/4/4.
 */
public class BucketRule {

    /**
     * 确定某个具体值的索引位置
     *
     * @param value
     * @return
     */

    public static int[] getShouldIndexPosition(long value) {

        long i = value >> (64 - AppConstants.BUCKET_FILE_BIT_NUM);
        long j = (value >> (64 - AppConstants.FILE_INNER_BUCKET_BIT_NUM)) % AppConstants.FILE_INNER_BUCKET_NUM;
        int[] result = new int[2];
        result[0] = (int) i;
        result[1] = (int) j;
        return result;
    }

    /**
     * 读取数据时，定位k,n的文件page位置
     *
     * @param k
     * @param n
     * @return
     */
    public static List<int[]> getKNIndexPosition(long k, int n, SeqIdsIndex seqIdsIndex) {

        int[] beginPosition = seqIdsIndex.getIndexPosition(k);
        int[] endPosition = seqIdsIndex.getIndexPosition(k + n - 1);

        List<int[]> result = new ArrayList<>();
        result.add(beginPosition);

        if (endPosition[0] != beginPosition[0] || endPosition[1] != beginPosition[1]) {
            result.add(endPosition);
        }

        return result;
    }


    public static String getSourceDataFileName(int i) {
        return KNLimit.DATA_DIR + KNLimit.FILE_PREFIX + i + KNLimit.FILE_SUFFIX;
    }

    public static String getSourceTestDataFileName(int i) {
        return KNLimit.DATA_DIR + "test_" + i + KNLimit.FILE_SUFFIX;
    }

    public static String getIndexDataFileName(int i) {
        return AppConstants.INDEX_DATA_DIR + i + ".data";
    }

    public static String getIndexInfoFileName(int i) {
        return AppConstants.INDEX_INFO_DIR + i + ".index";
    }

    public static String getGlobalIndexInfoFileName() {
        return KNLimit.MIDDLE_DIR + "global.index";
    }

    public static String getResultFileName() {
        return KNLimit.RESULT_DIR + KNLimit.RESULT_NAME;
    }

    public static void main(String[] args) {

//        Random random = new Random(System.currentTimeMillis());
//        for (int j = 0; j < 100; j++) {
//            Long randomValue = Math.abs(random.nextLong());
//            int[] a1 = getShouldIndexPosition(randomValue);
//            for (int i = 0; i < a1.length; i++) {
//                System.out.println(a1[i]);
//            }
//
//            a1 = getFastShouldIndexPosition(randomValue);
//            for (int i = 0; i < a1.length; i++) {
//                System.out.println(a1[i]);
//            }
//            System.out.println("++++++++");
//        }
//
//
//        System.out.println(Math.log(128) / Math.log(2));

    }

}
