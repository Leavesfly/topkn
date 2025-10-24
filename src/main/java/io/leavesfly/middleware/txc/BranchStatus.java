package io.leavesfly.middleware.txc;

/**
 * 分支事务状态
 */
public enum BranchStatus {
    /**
     * 初始化
     */
    INIT,
    
    /**
     * 执行中
     */
    EXECUTING,
    
    /**
     * 执行成功
     */
    SUCCESS,
    
    /**
     * 执行失败
     */
    FAILED,
    
    /**
     * 补偿中
     */
    COMPENSATING,
    
    /**
     * 补偿成功
     */
    COMPENSATED,
    
    /**
     * 补偿失败
     */
    COMPENSATE_FAILED
}
