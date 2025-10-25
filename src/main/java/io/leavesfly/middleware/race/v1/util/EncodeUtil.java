package io.leavesfly.middleware.race.v1.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class EncodeUtil {

    public static final int LETTER_INT_OFFSET = 48;
    public static final byte LINE_CODE = (byte) ('\n');
    public static final int RADIX = 10;

    /**
     * @param prexInput
     * @param input
     * @param longValues
     * @return
     */
    public static byte[] charDigitByte2Long(byte[] prexInput, byte[] input, List<Long> longValues) {

        if (prexInput != null) {
            input = merge(prexInput, input);
        }
        int size = input.length;
        int i = 0;
        int j = 0;
        for (; i < size; i++) {
            if (input[i] == LINE_CODE) {
                long radix = 1;
                int between = i - j;
                long value = 0;
                for (int index = 0; index < between; index++) {
                    value += ((int) input[i - index - 1] - LETTER_INT_OFFSET) * radix;
                    radix *= RADIX;
                }
                j = i + 1;
                longValues.add(value);
            }
        }
        if (i >= j) {
            return Arrays.copyOfRange(input, j, i);
        } else {
            return null;
        }
    }


    public static List<Long> charBytes2Longs(byte[] input) {
        List<Long> longValues = new ArrayList<>();
        int size = input.length;
        int i = 0;
        int j = 0;
        for (; i < size; i++) {
            if (input[i] == LINE_CODE) {
                long radix = 1;
                int between = i - j;
                long value = 0;
                for (int index = 0; index < between; index++) {
                    value += ((int) input[i - index - 1] - LETTER_INT_OFFSET) * radix;
                    radix *= RADIX;
                }
                j = i + 1;
                longValues.add(value);
            }
        }
        return longValues;
    }


    public static long bytes2long(byte[] bytes) {
        long result = 0;
        int length = Math.min(8, bytes.length);
        for (int i = 0; i < length; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    /**
     * 只需要考虑正数
     *
     * @param value
     * @return
     */
    public static byte[] long2bytes(long value) {
        byte[] result = new byte[8];
        result[0] = (byte) (value >> 56);
        result[1] = (byte) (value >> 48);
        result[2] = (byte) (value >> 40);
        result[3] = (byte) (value >> 32);
        result[4] = (byte) (value >> 24);
        result[5] = (byte) (value >> 16);
        result[6] = (byte) (value >> 8);
        result[7] = (byte) (value);
        return result;
    }

    public static long[] convert(List<Long> data) {
        int size = data.size();
        long[] result = new long[size];
        int index = 0;
        for (long value : data) {
            result[index] = value;
            index++;
        }
        return result;
    }

    public static long[] merge(long[] part1, long[] part2) {
        int size = part1.length + part2.length;
        long[] result = new long[size];
        System.arraycopy(part1, 0, result, 0, part1.length);
        System.arraycopy(part2, 0, result, part1.length, part2.length);
        return result;
    }

    public static byte[] merge(byte[] part1, byte[] part2) {
        int size = part1.length + part2.length;
        byte[] result = new byte[size];
        System.arraycopy(part1, 0, result, 0, part1.length);
        System.arraycopy(part2, 0, result, part1.length, part2.length);
        return result;
    }

}
