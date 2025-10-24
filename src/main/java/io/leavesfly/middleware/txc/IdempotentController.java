package io.leavesfly.middleware.txc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性控制器
 * 防止事务重复执行
 */
public class IdempotentController {
    
    // 事务执行记录（事务ID -> 过期时间）
    private ConcurrentHashMap<String, Long> executionRecords;
    
    // 记录过期时间（毫秒）默认1小时
    private long expireTime = 3600000L;
    
    public IdempotentController() {
        this.executionRecords = new ConcurrentHashMap<String, Long>();
        
        // 启动后台清理任务
        startCleanupTask();
    }
    
    /**
     * 尝试获取执行权限
     * @param txId 事务ID
     * @return 是否获取成功
     */
    public boolean tryAcquire(String txId) {
        long currentTime = System.currentTimeMillis();
        Long expireTime = executionRecords.get(txId);
        
        // 如果记录不存在或已过期，允许执行
        if (expireTime == null || expireTime < currentTime) {
            executionRecords.put(txId, currentTime + this.expireTime);
            return true;
        }
        
        // 记录存在且未过期，拒绝执行
        return false;
    }
    
    /**
     * 释放执行权限
     * @param txId 事务ID
     */
    public void release(String txId) {
        // 不删除记录，保留用于幂等性判断，等待后台清理任务清理
    }
    
    /**
     * 启动后台清理任务
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        TimeUnit.MINUTES.sleep(10);
                        cleanup();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        cleanupThread.setName("Idempotent-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    /**
     * 清理过期记录
     */
    private void cleanup() {
        long currentTime = System.currentTimeMillis();
        
        for (String txId : executionRecords.keySet()) {
            Long expireTime = executionRecords.get(txId);
            if (expireTime != null && expireTime < currentTime) {
                executionRecords.remove(txId);
            }
        }
    }
}
