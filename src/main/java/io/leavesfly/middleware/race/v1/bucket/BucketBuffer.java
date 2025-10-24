package io.leavesfly.middleware.race.v1.bucket;


import io.leavesfly.middleware.race.v1.AppConstants;
import io.leavesfly.middleware.race.v1.util.EncodeUtil;


import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Created by yefei.yf on 2017/4/3.
 */
public class BucketBuffer {

    private int BUCKET_CACHE_CAPA = AppConstants.LONG_BYTE * AppConstants.LONG_BYTE_BUFFER_NUM;

    private BufferedOutputStream bufferedOutputStream;
    private byte[][] buffers;
    private int[] bufferOffsets;
    private final AtomicBoolean[] locks = new AtomicBoolean[AppConstants.FILE_INNER_BUCKET_NUM];

    private ConcurrentLinkedQueue<Long>[] concurrentLinkedQueues = new ConcurrentLinkedQueue[AppConstants.FILE_INNER_BUCKET_NUM];

    public BucketBuffer(FileOutputStream fileOutputStream) {

        this.bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        buffers = new byte[AppConstants.FILE_INNER_BUCKET_NUM][BUCKET_CACHE_CAPA];
        bufferOffsets = new int[AppConstants.FILE_INNER_BUCKET_NUM];
        for (int i = 0; i < AppConstants.FILE_INNER_BUCKET_NUM; i++) {
            locks[i] = new AtomicBoolean(false);
            concurrentLinkedQueues[i] = new ConcurrentLinkedQueue();
        }

    }

    public void writeData(long value, int bufferIndex, ElseNeedDo elseNeedDo) {

        if (locks[bufferIndex].compareAndSet(false, true)) {
            System.arraycopy(EncodeUtil.long2bytes(value), 0, buffers[bufferIndex], bufferOffsets[bufferIndex], AppConstants.LONG_BYTE);
            bufferOffsets[bufferIndex] += AppConstants.LONG_BYTE;

            if (bufferOffsets[bufferIndex] >= BUCKET_CACHE_CAPA) {
                bufferOffsets[bufferIndex] = 0;
                byte[] tmp = buffers[bufferIndex];
                buffers[bufferIndex] = new byte[BUCKET_CACHE_CAPA];
                locks[bufferIndex].set(false);

                synchronized (this) {
                    try {
                        bufferedOutputStream.write(tmp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    elseNeedDo.doSomething(bufferIndex);
                }
            }
            locks[bufferIndex].set(false);
        } else {
            concurrentLinkedQueues[bufferIndex].add(value);
        }
    }

    /**
     * @param elseNeedDo
     */

    public void flush(ElseNeedDo elseNeedDo, ElseNeedDo elseNeedDoAppand) {

        for (int i = 0; i < bufferOffsets.length; i++) {
            if (!concurrentLinkedQueues[i].isEmpty()) {
                Object[] values = concurrentLinkedQueues[i].toArray();
                for (Object value : values) {
                    writeData((Long) value, i, elseNeedDoAppand);
                }
            }

            if (bufferOffsets[i] != 0) {
                try {
                    //内存页对齐，便于寻址
                    Arrays.fill(buffers[i], bufferOffsets[i], BUCKET_CACHE_CAPA, (byte) AppConstants.INVALID_LONG_VALUE);
                    bufferedOutputStream.write(buffers[i]);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                elseNeedDo.doSomething(i, bufferOffsets[i]);
                bufferOffsets[i] = 0;
            }
        }
        try {
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    interface ElseNeedDo {
        /**
         * @param args
         */
        void doSomething(Object... args);
    }

}
