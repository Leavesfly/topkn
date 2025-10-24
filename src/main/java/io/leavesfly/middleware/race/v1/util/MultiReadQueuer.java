package io.leavesfly.middleware.race.v1.util;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.leavesfly.middleware.race.v1.TopKN;

/**
 * Created by yefei.yf on 2017/4/15.
 */
public class MultiReadQueuer {

    private InnerSingleWriteMultiReadQueue[] queues;
    private int queuesSize;

    public ConcurrentLinkedQueue<byte[]> buff;

    public volatile boolean isOver = false;

    public MultiReadQueuer(int queuesSize) {
        this.queuesSize = queuesSize;
        queues = new InnerSingleWriteMultiReadQueue[queuesSize];
        for (int i = 0; i < queuesSize; i++) {
            queues[i] = new InnerSingleWriteMultiReadQueue();
        }
        buff = new ConcurrentLinkedQueue<>();
    }


    public void put(byte[] bytes, int position) {
        queues[position % queuesSize].add(bytes);
    }

    public byte[] take(int position) {
        int count = 0;
        for (int i = position; count < queuesSize; ) {
            byte[] bytes = queues[i % queuesSize].take();
            if (bytes != null) {
                return bytes;
            }
            count++;
            i++;
        }
        return buff.poll();
    }


    class InnerSingleWriteMultiReadQueue {
        private LinkedList<byte[]> dataQueue;
        private AtomicBoolean isRead;

        public InnerSingleWriteMultiReadQueue() {
            dataQueue = new LinkedList<>();
            isRead = new AtomicBoolean(false);
        }

        public void add(byte[] bytes) {
//            //本地测试使用
            if (dataQueue.size() > 100) {
                TopKN.logger.info("XXXXXXXXXXXXXXXXXX Read Too Much...");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (isRead.compareAndSet(false, true)) {
                dataQueue.add(bytes);
                isRead.set(false);
            } else {
                TopKN.logger.info(" buffffffffffff...");
                buff.add(bytes);
            }
        }

        public byte[] take() {
            if (isRead.compareAndSet(false, true)) {
                byte[] result = dataQueue.pollFirst();
                isRead.set(false);
                return result;
            } else {
                return null;
            }
        }
    }
}
