package com.alibaba.middleware.race.v1.util;

import com.alibaba.middleware.race.bucket.IdRange;


/**
 * Created by yefei.yf on 2017/4/1.
 */
public class SearchUtil {

    /**
     * [0,100,200,300]
     * 0 输出 0
     * 1 输出 0
     * 99输出 0
     * 100 输出 1
     * 101 输出 1
     * 300 输出3
     * 301 输出 3
     *
     * @param value
     * @param sortedArray
     * @return
     */
    public static int getRangeIndex(long value, long[] sortedArray) {
        int low = 0;
        int high = sortedArray.length - 1;
        int middle = low;
        while (low <= high) {
            middle = low + (high - low) / 2;
            if (value < sortedArray[middle]) {
                high = middle - 1;
            } else if (value > sortedArray[middle]) {
                if (middle < sortedArray.length - 1
                        && value < sortedArray[middle + 1]) {
                    return middle;
                }
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return middle;
    }

    /**
     * @param value
     * @param sortedArray
     * @return
     */
    public static int searchIdRange(long value, IdRange[] sortedArray) {
        int low = 0;
        int high = sortedArray.length - 1;
        int middle = low;
        while (low <= high) {
            middle = low + (high - low) / 2;
            if (sortedArray[middle].isLeft(value)) {
                high = middle - 1;
            } else if (sortedArray[middle].isRight(value)) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return middle;
    }
}
