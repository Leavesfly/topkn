package com.alibaba.middleware.txc;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式事务管理器
 * 负责事务的开启、提交、回滚等核心功能
 */
public class TransactionManager {
    
    private static final Logger logger = Logger.getLogger(TransactionManager.class);
    
    // 单例
    private static TransactionManager instance = new TransactionManager();
    
    // 事务日志持久化
    private TransactionLog transactionLog;
    
    // 事务执行器线程池
    private ExecutorService executorService;
    
    // 补偿任务调度器
    private ScheduledExecutorService compensateScheduler;
    
    // 事务超时时间（毫秒）
    private long transactionTimeout = 30000L;
    
    // 事务ID生成器
    private AtomicLong txIdGenerator = new AtomicLong(0);
    
    // 幂等性控制器
    private IdempotentController idempotentController;
    
    private TransactionManager() {
        // 初始化线程池，根据CPU核心数调整
        int processors = Runtime.getRuntime().availableProcessors();
        this.executorService = new ThreadPoolExecutor(
                processors * 2,
                processors * 4,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(1000),
                new ThreadFactory() {
                    private AtomicLong threadNum = new AtomicLong(0);
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("TXC-Executor-" + threadNum.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 初始化补偿调度器
        this.compensateScheduler = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private AtomicLong threadNum = new AtomicLong(0);
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("TXC-Compensate-" + threadNum.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        
        // 初始化事务日志
        this.transactionLog = new TransactionLog();
        
        // 初始化幂等性控制器
        this.idempotentController = new IdempotentController();
        
        // 启动后台补偿任务
        startCompensateTask();
        
        logger.info("TransactionManager initialized");
    }
    
    public static TransactionManager getInstance() {
        return instance;
    }
    
    /**
     * 开始一个新事务
     */
    public Transaction beginTransaction() {
        String txId = generateTxId();
        Transaction tx = new Transaction(txId);
        tx.setStatus(TransactionStatus.INIT);
        
        // 持久化事务日志
        transactionLog.saveTransaction(tx);
        
        logger.info("Transaction started: " + txId);
        return tx;
    }
    
    /**
     * 添加事务分支
     */
    public void addBranch(Transaction tx, String branchName, 
                         TransactionAction action, 
                         TransactionAction compensateAction) {
        String branchId = tx.getTxId() + "-" + tx.getBranches().size();
        TransactionBranch branch = new TransactionBranch(branchId, tx.getTxId(), branchName);
        branch.setAction(action);
        branch.setCompensateAction(compensateAction);
        
        tx.addBranch(branch);
        
        // 持久化分支信息
        transactionLog.saveBranch(branch);
        
        logger.info("Branch added: " + branchId + ", name: " + branchName);
    }
    
    /**
     * 执行事务
     */
    public boolean executeTransaction(final Transaction tx) {
        // 幂等性检查
        if (!idempotentController.tryAcquire(tx.getTxId())) {
            logger.warn("Transaction already executed: " + tx.getTxId());
            return false;
        }
        
        try {
            tx.setStatus(TransactionStatus.EXECUTING);
            transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.EXECUTING);
            
            // 创建事务上下文
            final TransactionContext context = new TransactionContext(tx.getTxId());
            
            // 依次执行各个分支
            List<TransactionBranch> branches = tx.getBranches();
            for (int i = 0; i < branches.size(); i++) {
                final TransactionBranch branch = branches.get(i);
                
                try {
                    // 执行分支
                    boolean success = executeBranch(branch, context);
                    
                    if (!success) {
                        // 分支执行失败，触发回滚
                        logger.error("Branch execution failed: " + branch.getBranchId());
                        rollbackTransaction(tx, context);
                        return false;
                    }
                    
                    tx.incrementBranchIndex();
                    
                } catch (Exception e) {
                    logger.error("Branch execution error: " + branch.getBranchId(), e);
                    branch.setErrorMsg(e.getMessage());
                    rollbackTransaction(tx, context);
                    return false;
                }
            }
            
            // 所有分支执行成功，提交事务
            commitTransaction(tx);
            return true;
            
        } finally {
            // 释放幂等性控制
            idempotentController.release(tx.getTxId());
        }
    }
    
    /**
     * 执行单个分支
     */
    private boolean executeBranch(TransactionBranch branch, TransactionContext context) {
        branch.setStatus(BranchStatus.EXECUTING);
        transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.EXECUTING);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 执行业务逻辑
            Object result = branch.getAction().execute(context);
            
            long endTime = System.currentTimeMillis();
            branch.setExecuteTime(endTime - startTime);
            branch.setResult(result);
            branch.setStatus(BranchStatus.SUCCESS);
            
            transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.SUCCESS);
            
            logger.info("Branch executed successfully: " + branch.getBranchId() + 
                       ", time: " + branch.getExecuteTime() + "ms");
            return true;
            
        } catch (Exception e) {
            branch.setStatus(BranchStatus.FAILED);
            branch.setErrorMsg(e.getMessage());
            transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.FAILED);
            
            logger.error("Branch execution failed: " + branch.getBranchId(), e);
            return false;
        }
    }
    
    /**
     * 提交事务
     */
    private void commitTransaction(Transaction tx) {
        tx.setStatus(TransactionStatus.COMMITTED);
        tx.setEndTime(System.currentTimeMillis());
        
        transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.COMMITTED);
        
        logger.info("Transaction committed: " + tx.getTxId() + 
                   ", duration: " + (tx.getEndTime() - tx.getStartTime()) + "ms");
    }
    
    /**
     * 回滚事务（执行补偿逻辑）
     */
    private void rollbackTransaction(Transaction tx, TransactionContext context) {
        tx.setStatus(TransactionStatus.ROLLING_BACK);
        transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.ROLLING_BACK);
        
        logger.info("Rolling back transaction: " + tx.getTxId());
        
        // 逆序执行补偿操作
        List<TransactionBranch> branches = tx.getBranches();
        for (int i = tx.getCurrentBranchIndex() - 1; i >= 0; i--) {
            TransactionBranch branch = branches.get(i);
            
            // 只补偿已成功执行的分支
            if (branch.getStatus() == BranchStatus.SUCCESS) {
                compensateBranch(branch, context);
            }
        }
        
        tx.setStatus(TransactionStatus.ROLLED_BACK);
        tx.setEndTime(System.currentTimeMillis());
        transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.ROLLED_BACK);
        
        logger.info("Transaction rolled back: " + tx.getTxId());
    }
    
    /**
     * 补偿单个分支
     */
    private void compensateBranch(TransactionBranch branch, TransactionContext context) {
        if (branch.getCompensateAction() == null) {
            logger.warn("No compensate action for branch: " + branch.getBranchId());
            return;
        }
        
        branch.setStatus(BranchStatus.COMPENSATING);
        transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.COMPENSATING);
        
        try {
            branch.getCompensateAction().execute(context);
            
            branch.setStatus(BranchStatus.COMPENSATED);
            transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.COMPENSATED);
            
            logger.info("Branch compensated: " + branch.getBranchId());
            
        } catch (Exception e) {
            branch.setStatus(BranchStatus.COMPENSATE_FAILED);
            transactionLog.updateBranchStatus(branch.getBranchId(), BranchStatus.COMPENSATE_FAILED);
            
            logger.error("Branch compensate failed: " + branch.getBranchId(), e);
        }
    }
    
    /**
     * 启动后台补偿任务，定期检查失败的事务并重试
     */
    private void startCompensateTask() {
        compensateScheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    // 查询需要补偿的事务
                    List<Transaction> failedTransactions = transactionLog.queryFailedTransactions();
                    
                    for (Transaction tx : failedTransactions) {
                        // 检查是否超过最大重试次数
                        if (tx.getRetryCount() >= tx.getMaxRetryCount()) {
                            logger.warn("Transaction exceeded max retry count: " + tx.getTxId());
                            transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.FAILED);
                            continue;
                        }
                        
                        // 异步重试补偿
                        final Transaction finalTx = tx;
                        executorService.submit(new Runnable() {
                            public void run() {
                                retryCompensate(finalTx);
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    logger.error("Compensate task error", e);
                }
            }
        }, 10, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 重试补偿
     */
    private void retryCompensate(Transaction tx) {
        logger.info("Retry compensate for transaction: " + tx.getTxId());
        
        tx.incrementRetryCount();
        transactionLog.updateRetryCount(tx.getTxId(), tx.getRetryCount());
        
        TransactionContext context = new TransactionContext(tx.getTxId());
        
        List<TransactionBranch> branches = tx.getBranches();
        for (TransactionBranch branch : branches) {
            if (branch.getStatus() == BranchStatus.COMPENSATE_FAILED) {
                compensateBranch(branch, context);
            }
        }
        
        // 检查是否所有分支都补偿成功
        boolean allCompensated = true;
        for (TransactionBranch branch : branches) {
            if (branch.getStatus() == BranchStatus.SUCCESS && 
                branch.getStatus() != BranchStatus.COMPENSATED) {
                allCompensated = false;
                break;
            }
        }
        
        if (allCompensated) {
            tx.setStatus(TransactionStatus.ROLLED_BACK);
            transactionLog.updateTransactionStatus(tx.getTxId(), TransactionStatus.ROLLED_BACK);
            logger.info("Transaction compensated successfully after retry: " + tx.getTxId());
        }
    }
    
    /**
     * 生成事务ID
     */
    private String generateTxId() {
        return "TX-" + System.currentTimeMillis() + "-" + 
               txIdGenerator.incrementAndGet() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 关闭事务管理器
     */
    public void shutdown() {
        logger.info("Shutting down TransactionManager...");
        
        executorService.shutdown();
        compensateScheduler.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!compensateScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                compensateScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            compensateScheduler.shutdownNow();
        }
        
        transactionLog.close();
        
        logger.info("TransactionManager shutdown completed");
    }
}
