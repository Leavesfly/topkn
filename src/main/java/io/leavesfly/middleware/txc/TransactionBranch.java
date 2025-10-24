package io.leavesfly.middleware.txc;

import java.io.Serializable;

/**
 * 事务分支（代表一个远程调用）
 */
public class TransactionBranch implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 分支ID
    private String branchId;
    
    // 所属事务ID
    private String txId;
    
    // 分支名称
    private String branchName;
    
    // 分支状态
    private BranchStatus status;
    
    // 执行动作
    private TransactionAction action;
    
    // 补偿动作
    private TransactionAction compensateAction;
    
    // 执行结果
    private Object result;
    
    // 错误信息
    private String errorMsg;
    
    // 执行时间
    private long executeTime;
    
    public TransactionBranch(String branchId, String txId, String branchName) {
        this.branchId = branchId;
        this.txId = txId;
        this.branchName = branchName;
        this.status = BranchStatus.INIT;
    }
    
    public String getBranchId() {
        return branchId;
    }
    
    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }
    
    public String getTxId() {
        return txId;
    }
    
    public void setTxId(String txId) {
        this.txId = txId;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public BranchStatus getStatus() {
        return status;
    }
    
    public void setStatus(BranchStatus status) {
        this.status = status;
    }
    
    public TransactionAction getAction() {
        return action;
    }
    
    public void setAction(TransactionAction action) {
        this.action = action;
    }
    
    public TransactionAction getCompensateAction() {
        return compensateAction;
    }
    
    public void setCompensateAction(TransactionAction compensateAction) {
        this.compensateAction = compensateAction;
    }
    
    public Object getResult() {
        return result;
    }
    
    public void setResult(Object result) {
        this.result = result;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
    
    public long getExecuteTime() {
        return executeTime;
    }
    
    public void setExecuteTime(long executeTime) {
        this.executeTime = executeTime;
    }
}
