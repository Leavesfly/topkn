package io.leavesfly.middleware.txc;

/**
 * 事务状态枚举
 */
public enum TransactionStatus {
    /**
     * 初始化状态
     */
    INIT,
    
    /**
     * 执行中
     */
    EXECUTING,
    
    /**
     * 已提交
     */
    COMMITTED,
    
    /**
     * 回滚中
     */
    ROLLING_BACK,
    
    /**
     * 已回滚
     */
    ROLLED_BACK,
    
    /**
     * 失败
     */
    FAILED,
    
    /**
     * 超时
     */
    TIMEOUT
}
