package io.leavesfly.middleware.race.v1.bucket;

import io.leavesfly.middleware.race.KNLimit;
import io.leavesfly.middleware.race.v1.AppConstants;

import java.util.ArrayList;
import java.util.List;

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




    public static String getIndexDataFileName(int i) {
        return AppConstants.DATA_DIR_PATH + i + ".data";
    }

    public static String getIndexInfoFileName(int i) {
        return AppConstants.INDEX_DIR + i + ".index";
    }

    public static String getGlobalIndexInfoFileName() {
        return KNLimit.MIDDLE_DIR + "global.index";
    }

    public static String getResultFileName() {
        return KNLimit.RESULT_DIR + KNLimit.RESULT_NAME;
    }


}
