package com.alibaba.middleware.txc;

import org.apache.log4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TXC框架高并发测试
 * 模拟多个用户同时进行积分兑换
 */
public class ConcurrentTxcTest {
    
    private static final Logger logger = Logger.getLogger(ConcurrentTxcTest.class);
    
    private static AtomicInteger successCount = new AtomicInteger(0);
    private static AtomicInteger failCount = new AtomicInteger(0);
    
    public static void main(String[] args) {
        int concurrentUsers = 100; // 并发用户数
        
        logger.info("开始高并发测试，并发用户数: " + concurrentUsers);
        
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(concurrentUsers);
        
        long startTime = System.currentTimeMillis();
        
        // 创建并发任务
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // 等待统一启动信号
                        startLatch.await();
                        
                        // 执行积分兑换事务
                        boolean result = executeTransaction("user" + userId);
                        
                        if (result) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        logger.error("事务执行异常", e);
                        failCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                }
            }).start();
        }
        
        // 发送启动信号
        startLatch.countDown();
        
        // 等待所有任务完成
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 输出测试结果
        logger.info("========== 测试结果 ==========");
        logger.info("总并发数: " + concurrentUsers);
        logger.info("成功数: " + successCount.get());
        logger.info("失败数: " + failCount.get());
        logger.info("总耗时: " + duration + "ms");
        logger.info("平均耗时: " + (duration / concurrentUsers) + "ms");
        logger.info("TPS: " + (concurrentUsers * 1000 / duration));
        logger.info("============================");
        
        // 等待一段时间，让补偿任务有机会执行
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 关闭事务管理器
        TransactionManager.getInstance().shutdown();
    }
    
    /**
     * 执行一个积分兑换事务
     */
    private static boolean executeTransaction(final String userId) {
        TransactionManager txManager = TransactionManager.getInstance();
        
        // 开始事务
        Transaction tx = txManager.beginTransaction();
        
        // 添加第一个分支：扣除积分
        txManager.addBranch(
            tx,
            "扣除积分-" + userId,
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    // 模拟扣除积分
                    simulateRemoteCall(50); // 50ms延迟
                    
                    context.put("userId", userId);
                    context.put("points", 100);
                    
                    return true;
                }
            },
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    // 模拟补偿：恢复积分
                    simulateRemoteCall(30);
                    return true;
                }
            }
        );
        
        // 添加第二个分支：增加权益
        txManager.addBranch(
            tx,
            "增加权益-" + userId,
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    // 模拟增加权益
                    simulateRemoteCall(50);
                    
                    context.put("benefitType", "vip_days");
                    context.put("benefitAmount", 7);
                    
                    // 模拟5%的失败率，测试补偿机制
                    if (Math.random() < 0.05) {
                        throw new Exception("模拟增加权益失败");
                    }
                    
                    return true;
                }
            },
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    // 模拟补偿：扣除权益
                    simulateRemoteCall(30);
                    return true;
                }
            }
        );
        
        // 执行事务
        return txManager.executeTransaction(tx);
    }
    
    /**
     * 模拟远程调用延迟
     */
    private static void simulateRemoteCall(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
