package com.alibaba.middleware.race.v1;

import com.alibaba.middleware.race.bucket.*;
import com.alibaba.middleware.race.util.EncodeUtil;
import com.alibaba.middleware.race.util.SerializeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class BucketWriterExecutor {

    private FileBucketWriter[] bucketWriters;

    private BlockingQueue<List<Long>> blockingQueue;

    private Thread[] threads;

    private AtomicInteger sourceReaderOverCount;

    private CountDownLatch runOver;


    public BucketWriterExecutor(FileBucketWriter[] bucketWriters, CountDownLatch runOver, AtomicInteger sourceReaderOverCount) {
        this.bucketWriters = bucketWriters;
        blockingQueue = new ArrayBlockingQueue(bucketWriters.length);
        threads = new Thread[AppConstants.SOURCE_FILE_NUM];
        this.runOver = runOver;
        this.sourceReaderOverCount = sourceReaderOverCount;
    }

    public void put(List<Long> longValues) {
        try {
            blockingQueue.put(longValues);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                final long time = System.currentTimeMillis();

                final CountDownLatch latch = new CountDownLatch(AppConstants.SOURCE_FILE_NUM);
                for (int i = 0; i < threads.length; i++) {
                    final int finalI = i;
                    threads[i] = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (true) {
                                List<Long> longValues = null;
                                try {
                                    longValues = blockingQueue.poll(0, TimeUnit.MILLISECONDS);

                                    if (longValues == null
                                            && sourceReaderOverCount.get() == AppConstants.SOURCE_FILE_NUM) {
                                        break;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                if (longValues != null) {
                                    if (longValues.isEmpty()) {
                                        break;
                                    }
                                    for (long value : longValues) {
                                        int[] indexPosition = BucketRule.getShouldIndexPosition(value);
                                        bucketWriters[indexPosition[0]].writeData(value, indexPosition[1]);
                                    }
                                }
                            }
                            latch.countDown();
                            System.out.println(" asy :" + threads[finalI].getName() + ":" + (System.currentTimeMillis() - time));
                        }
                    });
                    threads[i].start();
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println(" asy :" + (System.currentTimeMillis() - time));

                long time1 = System.currentTimeMillis();

                //刷新索引数据
                for (FileBucketWriter bucketWriter : bucketWriters) {
                    bucketWriter.flush();
                }
                System.out.println(" bucketWriter.flush() :" + (System.currentTimeMillis() - time1));
                runOver.countDown();
            }
        }).start();
    }


    public static void main(String[] args) {
        Object lock = new Object();
        AtomicBoolean lock1 = new AtomicBoolean(false);
        long time = System.currentTimeMillis();
        for (int i = 0; i < 500_000_000l; i++) {
            synchronized (lock) {
            }
        }

        System.out.println(System.currentTimeMillis() - time);
        time = System.currentTimeMillis();

        for (int i = 0; i < 500_000_000l; i++) {
            lock1.compareAndSet(false, false);
        }
        System.out.println(System.currentTimeMillis() - time);
    }
}