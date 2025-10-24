package com.alibaba.middleware.txc;

import org.apache.log4j.Logger;

/**
 * TXC分布式事务框架使用示例
 * 
 * 演示场景：积分兑换业务
 * - 第一步：扣除用户积分
 * - 第二步：增加用户权益
 */
public class TxcExample {
    
    private static final Logger logger = Logger.getLogger(TxcExample.class);
    
    public static void main(String[] args) {
        // 示例：积分兑换
        executePointExchangeTransaction("user123", 100, "vip_days", 7);
        
        // 等待异步任务完成
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 关闭事务管理器
        TransactionManager.getInstance().shutdown();
    }
    
    /**
     * 执行积分兑换事务
     * @param userId 用户ID
     * @param points 扣除的积分数
     * @param benefitType 权益类型
     * @param benefitAmount 权益数量
     */
    public static void executePointExchangeTransaction(
            final String userId, 
            final int points, 
            final String benefitType, 
            final int benefitAmount) {
        
        TransactionManager txManager = TransactionManager.getInstance();
        
        // 1. 开始事务
        Transaction tx = txManager.beginTransaction();
        logger.info("开始积分兑换事务: " + tx.getTxId());
        
        // 2. 添加第一个分支：扣除积分
        txManager.addBranch(
            tx,
            "扣除用户积分",
            // 执行动作：扣除积分
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    logger.info("执行：扣除用户 " + userId + " 的 " + points + " 积分");
                    
                    // 模拟调用远程接口扣除积分
                    boolean success = deductPoints(userId, points);
                    
                    if (!success) {
                        throw new Exception("扣除积分失败：积分不足");
                    }
                    
                    // 将扣除结果存入上下文，供后续使用
                    context.put("deductedPoints", points);
                    context.put("userId", userId);
                    
                    logger.info("扣除积分成功");
                    return success;
                }
            },
            // 补偿动作：恢复积分
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    logger.info("补偿：恢复用户 " + userId + " 的 " + points + " 积分");
                    
                    // 模拟调用远程接口恢复积分
                    Integer deductedPoints = (Integer) context.get("deductedPoints");
                    if (deductedPoints != null) {
                        addPoints(userId, deductedPoints);
                    }
                    
                    logger.info("积分恢复成功");
                    return true;
                }
            }
        );
        
        // 3. 添加第二个分支：增加权益
        txManager.addBranch(
            tx,
            "增加用户权益",
            // 执行动作：增加权益
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    logger.info("执行：为用户 " + userId + " 增加 " + benefitAmount + " " + benefitType);
                    
                    // 模拟调用远程接口增加权益
                    boolean success = addBenefit(userId, benefitType, benefitAmount);
                    
                    if (!success) {
                        throw new Exception("增加权益失败：系统错误");
                    }
                    
                    // 将权益信息存入上下文
                    context.put("benefitType", benefitType);
                    context.put("benefitAmount", benefitAmount);
                    
                    logger.info("增加权益成功");
                    return success;
                }
            },
            // 补偿动作：扣除权益
            new TransactionAction() {
                public Object execute(TransactionContext context) throws Exception {
                    logger.info("补偿：扣除用户 " + userId + " 的 " + benefitAmount + " " + benefitType);
                    
                    // 模拟调用远程接口扣除权益
                    String type = (String) context.get("benefitType");
                    Integer amount = (Integer) context.get("benefitAmount");
                    
                    if (type != null && amount != null) {
                        deductBenefit(userId, type, amount);
                    }
                    
                    logger.info("权益扣除成功");
                    return true;
                }
            }
        );
        
        // 4. 执行事务
        boolean result = txManager.executeTransaction(tx);
        
        if (result) {
            logger.info("积分兑换事务执行成功: " + tx.getTxId());
        } else {
            logger.error("积分兑换事务执行失败: " + tx.getTxId());
        }
    }
    
    // ========== 模拟远程接口调用 ==========
    
    /**
     * 模拟扣除积分接口
     */
    private static boolean deductPoints(String userId, int points) {
        // 模拟网络延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 模拟业务逻辑
        // 这里可以调用真实的远程接口
        logger.info("[远程调用] 扣除积分接口 - userId: " + userId + ", points: " + points);
        
        return true;
    }
    
    /**
     * 模拟增加积分接口
     */
    private static boolean addPoints(String userId, int points) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        logger.info("[远程调用] 增加积分接口 - userId: " + userId + ", points: " + points);
        
        return true;
    }
    
    /**
     * 模拟增加权益接口
     */
    private static boolean addBenefit(String userId, String benefitType, int amount) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        logger.info("[远程调用] 增加权益接口 - userId: " + userId + 
                   ", type: " + benefitType + ", amount: " + amount);
        
        // 模拟10%的失败率，用于测试补偿机制
        // return Math.random() > 0.1;
        
        return true;
    }
    
    /**
     * 模拟扣除权益接口
     */
    private static boolean deductBenefit(String userId, String benefitType, int amount) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        logger.info("[远程调用] 扣除权益接口 - userId: " + userId + 
                   ", type: " + benefitType + ", amount: " + amount);
        
        return true;
    }
}
