package io.leavesfly.middleware.txc;

/**
 * 事务动作接口（执行或补偿）
 */
public interface TransactionAction {
    /**
     * 执行业务逻辑
     * @param context 事务上下文
     * @return 执行结果
     * @throws Exception 执行异常
     */
    Object execute(TransactionContext context) throws Exception;
}
