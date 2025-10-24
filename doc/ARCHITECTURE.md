# TXC 分布式事务框架 - 架构设计

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      应用层                                   │
│  (业务代码调用TXC框架执行分布式事务)                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   TransactionManager                        │
│  (事务管理器 - 框架核心，负责事务的全生命周期管理)              │
│                                                             │
│  • beginTransaction()      - 开始事务                        │
│  • addBranch()            - 添加分支                         │
│  • executeTransaction()   - 执行事务                         │
│  • rollbackTransaction()  - 回滚事务                         │
│  • shutdown()             - 关闭管理器                       │
└─────────────────────────────────────────────────────────────┘
         ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  Transaction │ │   Executor   │ │ Idempotent   │ │ Transaction  │
│   Object     │ │  ThreadPool  │ │  Controller  │ │     Log      │
│              │ │              │ │              │ │  (SQLite)    │
│ • 事务状态   │ │ • 异步执行   │ │ • 幂等控制   │ │ • 持久化     │
│ • 分支列表   │ │ • 并发控制   │ │ • 防重执行   │ │ • 恢复机制   │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
         ↓                                                  ↓
┌─────────────────────────────────────────────────────────────┐
│               TransactionBranch (分支)                      │
│  • 执行动作 (Action)                                        │
│  • 补偿动作 (Compensate)                                    │
│  • 分支状态                                                 │
└─────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────┐
│            远程服务 (Remote Services)                        │
│  • 积分服务 (扣除/增加积分)                                  │
│  • 权益服务 (增加/扣除权益)                                  │
│  • 其他业务服务                                             │
└─────────────────────────────────────────────────────────────┘
```

## 核心组件详解

### 1. TransactionManager (事务管理器)

**职责**:
- 事务生命周期管理
- 线程池管理
- 补偿任务调度
- 幂等性控制集成

**关键方法**:
```java
public Transaction beginTransaction()
public void addBranch(Transaction tx, String branchName, 
                     TransactionAction action, 
                     TransactionAction compensateAction)
public boolean executeTransaction(Transaction tx)
private void rollbackTransaction(Transaction tx, TransactionContext ctx)
private void startCompensateTask()
```

**线程模型**:
- 主执行线程池: CPU核心数 × 2 到 CPU核心数 × 4
- 补偿调度线程池: 2个固定线程
- 任务队列: LinkedBlockingQueue (容量1000)

### 2. Transaction (事务对象)

**属性**:
```java
- txId: String                          // 全局唯一事务ID
- status: TransactionStatus             // 事务状态
- branches: List<TransactionBranch>     // 分支列表
- currentBranchIndex: AtomicInteger     // 当前执行索引
- retryCount: int                       // 重试次数
- startTime/endTime: long               // 时间戳
```

**状态流转**:
```
INIT → EXECUTING → COMMITTED (成功路径)
  ↓
INIT → EXECUTING → ROLLING_BACK → ROLLED_BACK (失败路径)
  ↓
INIT → EXECUTING → FAILED (补偿失败)
```

### 3. TransactionBranch (事务分支)

**属性**:
```java
- branchId: String                  // 分支ID
- txId: String                      // 所属事务ID
- status: BranchStatus              // 分支状态
- action: TransactionAction         // 执行动作
- compensateAction: TransactionAction // 补偿动作
- result: Object                    // 执行结果
- errorMsg: String                  // 错误信息
```

**状态流转**:
```
执行路径:
INIT → EXECUTING → SUCCESS (成功)
INIT → EXECUTING → FAILED (失败)

补偿路径:
SUCCESS → COMPENSATING → COMPENSATED (补偿成功)
SUCCESS → COMPENSATING → COMPENSATE_FAILED (补偿失败)
```

### 4. TransactionLog (事务日志)

**存储方案**:
- 主存储: SQLite (轻量级、无需额外服务)
- 降级方案: 内存模式 (SQLite不可用时)

**数据表设计**:

```sql
-- 事务表
CREATE TABLE txc_transaction (
    tx_id VARCHAR(128) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    retry_count INT DEFAULT 0,
    max_retry_count INT DEFAULT 3,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

-- 分支表
CREATE TABLE txc_branch (
    branch_id VARCHAR(128) PRIMARY KEY,
    tx_id VARCHAR(128) NOT NULL,
    branch_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    error_msg TEXT,
    execute_time BIGINT,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    FOREIGN KEY (tx_id) REFERENCES txc_transaction(tx_id)
);

-- 索引
CREATE INDEX idx_tx_status ON txc_transaction(status);
CREATE INDEX idx_branch_tx_id ON txc_branch(tx_id);
CREATE INDEX idx_branch_status ON txc_branch(status);
```

### 5. IdempotentController (幂等性控制器)

**实现机制**:
```java
ConcurrentHashMap<txId, expireTime>

- 执行前检查: tryAcquire(txId)
- 记录过期时间: 1小时
- 后台清理: 每10分钟清理过期记录
```

**幂等性保证**:
```
第一次执行: tryAcquire() → true → 执行事务
重复执行: tryAcquire() → false → 拒绝执行
```

## 执行流程详解

### 正常执行流程

```
1. 应用调用 beginTransaction()
   ↓
2. TransactionManager 创建 Transaction 对象
   ↓ (生成唯一txId)
3. 持久化事务到 TransactionLog
   ↓
4. 应用调用 addBranch() 添加分支
   ↓
5. 持久化分支到 TransactionLog
   ↓
6. 应用调用 executeTransaction()
   ↓
7. 幂等性检查 (IdempotentController.tryAcquire)
   ↓
8. 依次执行各分支的 action.execute()
   ↓ (每个分支成功后更新状态)
9. 所有分支执行成功
   ↓
10. 提交事务 (状态 → COMMITTED)
   ↓
11. 持久化最终状态
```

### 补偿回滚流程

```
1. 某个分支执行失败 (抛出异常)
   ↓
2. TransactionManager 捕获异常
   ↓
3. 更新事务状态 → ROLLING_BACK
   ↓
4. 逆序遍历已执行成功的分支
   ↓
5. 执行每个分支的 compensateAction.execute()
   ↓
6. 更新分支状态 → COMPENSATED
   ↓
7. 所有补偿完成
   ↓
8. 更新事务状态 → ROLLED_BACK
   ↓
9. 持久化最终状态
```

### 补偿重试流程

```
1. 定时任务启动 (30秒执行一次)
   ↓
2. 查询状态为 ROLLING_BACK 或 FAILED 的事务
   ↓
3. 检查重试次数 < maxRetryCount
   ↓
4. 异步提交重试任务
   ↓
5. 重新执行补偿逻辑
   ↓
6. 更新重试次数
   ↓
7. 如果成功，更新状态 → ROLLED_BACK
   ↓
8. 如果失败且超过最大重试次数 → FAILED (需人工介入)
```

## 并发控制

### 事务级别并发

```java
// 多个事务可以并发执行
ExecutorService executorService = new ThreadPoolExecutor(
    processors * 2,      // 核心线程数
    processors * 4,      // 最大线程数
    60L,                 // 空闲超时
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<Runnable>(1000),
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
```

### 分支级别串行

```java
// 同一事务内的分支顺序执行
for (int i = 0; i < branches.size(); i++) {
    TransactionBranch branch = branches.get(i);
    executeBranch(branch, context);  // 同步执行
}
```

### 幂等性控制

```java
// 基于ConcurrentHashMap的线程安全实现
public boolean tryAcquire(String txId) {
    Long expireTime = executionRecords.get(txId);
    if (expireTime == null || expireTime < currentTime) {
        executionRecords.put(txId, currentTime + this.expireTime);
        return true;
    }
    return false;
}
```

## 可靠性保证

### 1. 持久化保证

- 事务开始时立即持久化
- 每个分支添加时持久化
- 状态变更时实时更新
- 应用崩溃后可恢复

### 2. 补偿保证

- 补偿动作必须幂等
- 自动重试机制 (最多3次)
- 补偿失败会持久化状态
- 定期扫描未完成的补偿

### 3. 幂等性保证

- 事务ID全局唯一
- 执行前幂等性检查
- 记录保留1小时
- 防止并发重复执行

## 性能优化

### 1. 线程池复用

避免频繁创建销毁线程，使用固定大小的线程池。

### 2. 批量持久化

可以优化为批量提交SQL，减少IO次数。

### 3. 异步补偿

补偿任务异步执行，不阻塞主流程。

### 4. 内存优化

使用ConcurrentHashMap存储运行时数据，避免锁竞争。

### 5. 数据库索引

在status、tx_id等字段上建立索引，加速查询。

## 扩展性设计

### 横向扩展

```
应用实例1 → TransactionManager1 → SQLite1
应用实例2 → TransactionManager2 → SQLite2
应用实例3 → TransactionManager3 → SQLite3
```

每个应用实例独立运行，无需中心化协调。

### 纵向扩展

- 可替换TransactionLog实现（使用MySQL、Redis等）
- 可自定义IdempotentController实现
- 可扩展监控和告警机制

## 限制与约束

### 技术限制

- JDK 1.7（基于项目要求）
- 禁用java.nio（基于项目要求）
- 禁用堆外内存（基于项目要求）
- 只使用JavaSE标准库 + log4j

### 业务限制

- 最终一致性（非强一致性）
- 补偿必须幂等
- 不支持嵌套事务
- 不支持分布式锁

### 性能限制

- SQLite写并发有限（可考虑替换）
- 单机内存限制事务并发数
- 网络延迟影响整体性能

## 设计模式

### 1. 单例模式
```java
TransactionManager.getInstance()
```

### 2. 策略模式
```java
TransactionAction (执行策略)
```

### 3. 模板方法模式
```java
executeTransaction() 定义事务执行模板
```

### 4. 观察者模式
```java
事务状态变更 → 持久化
```

## 总结

TXC框架采用经典的补偿事务（Saga）模式，通过以下机制保证分布式事务的最终一致性：

1. **执行时**: 顺序执行各分支，任一失败立即回滚
2. **回滚时**: 逆序执行补偿，恢复已执行的操作
3. **持久化**: 全程记录事务状态，支持崩溃恢复
4. **重试**: 自动重试补偿，最大限度保证最终一致
5. **幂等**: 防止重复执行，保证数据一致性

该设计在性能、可靠性和复杂度之间取得了良好的平衡，适合积分兑换等对一致性要求不是特别严格的业务场景。
