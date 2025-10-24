# TXC 轻量级分布式事务框架

## 概述

TXC是一个轻量级的分布式事务框架，专门用于处理类似积分兑换的业务场景。它支持高并发、保证最终一致性、具备幂等性控制，并使用SQLite作为轻量级持久化存储方案。

## 核心特性

1. **最终一致性保证**：通过补偿机制（Compensation）实现分布式事务的最终一致性
2. **高并发支持**：基于线程池的异步执行模型，支持高并发场景
3. **幂等性控制**：防止事务重复执行导致的数据不一致
4. **自动补偿重试**：后台定期检查失败的事务并自动重试补偿
5. **轻量级持久化**：使用SQLite存储事务状态，无需额外的数据库服务
6. **独立部署**：与业务代码完全解耦，可独立使用

## 架构设计

### 核心组件

- **TransactionManager**: 事务管理器，负责事务的生命周期管理
- **Transaction**: 事务对象，包含事务状态和分支信息
- **TransactionBranch**: 事务分支，代表一个远程调用
- **TransactionAction**: 事务动作接口，定义执行和补偿逻辑
- **TransactionContext**: 事务上下文，用于在分支间传递数据
- **TransactionLog**: 事务日志，基于SQLite的持久化存储
- **IdempotentController**: 幂等性控制器，防止重复执行

### 事务执行流程

```
1. 开始事务 (BEGIN)
   ↓
2. 添加分支 (ADD_BRANCH)
   ↓
3. 执行分支1 (EXECUTE)
   ↓
4. 执行分支2 (EXECUTE)
   ↓
5. 提交事务 (COMMIT) 或 回滚事务 (ROLLBACK)
   ↓
6. 如果回滚，执行补偿逻辑 (COMPENSATE)
```

## 使用指南

### 基本用法

```java
// 1. 获取事务管理器实例
TransactionManager txManager = TransactionManager.getInstance();

// 2. 开始一个新事务
Transaction tx = txManager.beginTransaction();

// 3. 添加事务分支（第一个远程调用：扣除积分）
txManager.addBranch(
    tx,
    "扣除用户积分",
    // 执行动作
    new TransactionAction() {
        public Object execute(TransactionContext context) throws Exception {
            // 调用远程接口扣除积分
            deductPoints(userId, points);
            // 保存数据到上下文
            context.put("deductedPoints", points);
            return true;
        }
    },
    // 补偿动作
    new TransactionAction() {
        public Object execute(TransactionContext context) throws Exception {
            // 调用远程接口恢复积分
            Integer points = (Integer) context.get("deductedPoints");
            addPoints(userId, points);
            return true;
        }
    }
);

// 4. 添加第二个分支（第二个远程调用：增加权益）
txManager.addBranch(
    tx,
    "增加用户权益",
    // 执行动作
    new TransactionAction() {
        public Object execute(TransactionContext context) throws Exception {
            // 调用远程接口增加权益
            addBenefit(userId, benefitType, amount);
            context.put("benefitAmount", amount);
            return true;
        }
    },
    // 补偿动作
    new TransactionAction() {
        public Object execute(TransactionContext context) throws Exception {
            // 调用远程接口扣除权益
            Integer amount = (Integer) context.get("benefitAmount");
            deductBenefit(userId, benefitType, amount);
            return true;
        }
    }
);

// 5. 执行事务
boolean result = txManager.executeTransaction(tx);

// 6. 关闭事务管理器（应用退出时调用）
txManager.shutdown();
```

### 积分兑换示例

参考 `TxcExample.java` 文件中的完整示例：

```java
public class TxcExample {
    public static void executePointExchangeTransaction(
            String userId, int points, 
            String benefitType, int benefitAmount) {
        
        TransactionManager txManager = TransactionManager.getInstance();
        Transaction tx = txManager.beginTransaction();
        
        // 添加扣除积分分支
        txManager.addBranch(tx, "扣除积分", executeAction, compensateAction);
        
        // 添加增加权益分支
        txManager.addBranch(tx, "增加权益", executeAction, compensateAction);
        
        // 执行事务
        txManager.executeTransaction(tx);
    }
}
```

## 配置说明

### 线程池配置

事务管理器内部使用线程池处理事务执行：

- **核心线程数**: CPU核心数 × 2
- **最大线程数**: CPU核心数 × 4
- **队列容量**: 1000

### 补偿重试配置

- **初始延迟**: 10秒
- **检查间隔**: 30秒
- **最大重试次数**: 3次（可通过Transaction.setMaxRetryCount()修改）

### 幂等性控制

- **记录过期时间**: 1小时
- **清理间隔**: 10分钟

## 技术限制

根据项目要求，本框架遵循以下限制：

- **JDK版本**: 1.7.0_80
- **依赖库**: 仅使用JavaSE 7标准库和log4j 1.2.17
- **禁止使用**: java.nio包、堆外内存、JNI、Shell调用
- **数据库**: SQLite（可选，如不可用则降级为内存模式）

## 持久化存储

事务状态和分支信息会持久化到SQLite数据库中：

- **数据库文件**: `data/txc_transaction.db`
- **事务表**: `txc_transaction`
- **分支表**: `txc_branch`

## 最佳实践

1. **合理设计补偿逻辑**: 每个执行动作都应该有对应的补偿动作
2. **使用事务上下文传递数据**: 通过TransactionContext在分支间共享数据
3. **处理幂等性**: 确保远程接口本身也具备幂等性
4. **异常处理**: 在TransactionAction中妥善处理异常
5. **事务超时**: 对于长时间运行的操作，考虑调整超时时间
6. **资源清理**: 应用退出时调用shutdown()方法

## 故障恢复

框架提供以下故障恢复机制：

1. **事务日志持久化**: 所有事务状态持久化到SQLite
2. **自动补偿重试**: 后台定期扫描失败的事务并重试
3. **最大重试次数限制**: 避免无限重试

## 性能考虑

1. **并发控制**: 使用ConcurrentHashMap和线程安全的数据结构
2. **异步执行**: 补偿任务异步执行，不阻塞主流程
3. **批量处理**: 支持多个事务并发执行
4. **资源隔离**: 事务管理器使用独立的线程池

## 监控和日志

框架使用log4j记录关键事件：

- 事务开始/提交/回滚
- 分支执行成功/失败
- 补偿执行成功/失败
- 重试信息

## 注意事项

1. **不适合强一致性场景**: 本框架实现的是最终一致性，不适合需要强一致性的场景
2. **网络分区**: 需要应用层处理网络分区导致的问题
3. **补偿失败**: 多次补偿失败后需要人工介入
4. **SQLite限制**: SQLite不适合高并发写入场景，如需更高性能可考虑其他持久化方案

## 扩展性

框架设计支持以下扩展：

1. **自定义持久化**: 实现TransactionLog接口，替换SQLite
2. **自定义幂等性控制**: 替换IdempotentController实现
3. **监控集成**: 在关键位置添加监控埋点
4. **分布式支持**: 可扩展为基于消息队列的分布式事务

## 许可证

本框架为内部使用框架，独立于项目其他模块。
