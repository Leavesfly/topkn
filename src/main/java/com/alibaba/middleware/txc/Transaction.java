package com.alibaba.middleware.txc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式事务对象
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 事务ID
    private String txId;
    
    // 事务状态
    private TransactionStatus status;
    
    // 事务开始时间
    private long startTime;
    
    // 事务结束时间
    private long endTime;
    
    // 事务分支列表
    private List<TransactionBranch> branches;
    
    // 当前执行到的分支索引
    private AtomicInteger currentBranchIndex;
    
    // 重试次数
    private int retryCount;
    
    // 最大重试次数
    private int maxRetryCount;
    
    public Transaction(String txId) {
        this.txId = txId;
        this.status = TransactionStatus.INIT;
        this.startTime = System.currentTimeMillis();
        this.branches = new ArrayList<TransactionBranch>();
        this.currentBranchIndex = new AtomicInteger(0);
        this.retryCount = 0;
        this.maxRetryCount = 3;
    }
    
    public void addBranch(TransactionBranch branch) {
        this.branches.add(branch);
    }
    
    public String getTxId() {
        return txId;
    }
    
    public void setTxId(String txId) {
        this.txId = txId;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public List<TransactionBranch> getBranches() {
        return branches;
    }
    
    public void setBranches(List<TransactionBranch> branches) {
        this.branches = branches;
    }
    
    public int getCurrentBranchIndex() {
        return currentBranchIndex.get();
    }
    
    public int incrementBranchIndex() {
        return currentBranchIndex.incrementAndGet();
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    public int getMaxRetryCount() {
        return maxRetryCount;
    }
    
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
}
