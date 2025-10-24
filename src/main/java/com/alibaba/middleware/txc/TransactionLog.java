package com.alibaba.middleware.txc;

import org.apache.log4j.Logger;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 事务日志持久化（使用SQLite）
 * 用于事务状态的持久化存储，支持事务恢复和补偿
 */
public class TransactionLog {
    
    private static final Logger logger = Logger.getLogger(TransactionLog.class);
    
    // SQLite数据库连接
    private Connection connection;
    
    // 数据库文件路径
    private String dbPath;
    
    public TransactionLog() {
        this("txc_transaction.db");
    }
    
    public TransactionLog(String dbName) {
        try {
            // 使用当前工作目录下的data目录
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            
            this.dbPath = dataDir.getAbsolutePath() + File.separator + dbName;
            
            // 加载SQLite JDBC驱动（使用Java 7兼容的方式）
            Class.forName("org.sqlite.JDBC");
            
            // 创建数据库连接
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // 初始化数据库表
            initTables();
            
            logger.info("TransactionLog initialized with SQLite, db path: " + dbPath);
            
        } catch (ClassNotFoundException e) {
            logger.warn("SQLite JDBC driver not found, using memory-only mode", e);
            connection = null;
        } catch (Exception e) {
            logger.error("Failed to initialize TransactionLog with SQLite", e);
            // 如果SQLite不可用，使用内存模式（不持久化）
            logger.warn("Fallback to memory-only mode");
            connection = null;
        }
    }
    
    /**
     * 初始化数据库表
     */
    private void initTables() throws SQLException {
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            
            // 创建事务表
            String createTxTable = "CREATE TABLE IF NOT EXISTS txc_transaction (" +
                    "tx_id VARCHAR(128) PRIMARY KEY," +
                    "status VARCHAR(32) NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "retry_count INT DEFAULT 0," +
                    "max_retry_count INT DEFAULT 3," +
                    "create_time BIGINT NOT NULL," +
                    "update_time BIGINT NOT NULL" +
                    ")";
            stmt.execute(createTxTable);
            
            // 创建分支表
            String createBranchTable = "CREATE TABLE IF NOT EXISTS txc_branch (" +
                    "branch_id VARCHAR(128) PRIMARY KEY," +
                    "tx_id VARCHAR(128) NOT NULL," +
                    "branch_name VARCHAR(128)," +
                    "status VARCHAR(32) NOT NULL," +
                    "error_msg TEXT," +
                    "execute_time BIGINT," +
                    "create_time BIGINT NOT NULL," +
                    "update_time BIGINT NOT NULL," +
                    "FOREIGN KEY (tx_id) REFERENCES txc_transaction(tx_id)" +
                    ")";
            stmt.execute(createBranchTable);
            
            // 创建索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_status ON txc_transaction(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_branch_tx_id ON txc_branch(tx_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_branch_status ON txc_branch(status)");
            
            logger.info("Database tables initialized");
            
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }
    
    /**
     * 保存事务
     */
    public void saveTransaction(Transaction tx) {
        if (connection == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        try {
            String sql = "INSERT INTO txc_transaction " +
                    "(tx_id, status, start_time, end_time, retry_count, max_retry_count, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            pstmt = connection.prepareStatement(sql);
            long now = System.currentTimeMillis();
            
            pstmt.setString(1, tx.getTxId());
            pstmt.setString(2, tx.getStatus().name());
            pstmt.setLong(3, tx.getStartTime());
            pstmt.setLong(4, tx.getEndTime());
            pstmt.setInt(5, tx.getRetryCount());
            pstmt.setInt(6, tx.getMaxRetryCount());
            pstmt.setLong(7, now);
            pstmt.setLong(8, now);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to save transaction: " + tx.getTxId(), e);
        } finally {
            closeStatement(pstmt);
        }
    }
    
    /**
     * 保存分支
     */
    public void saveBranch(TransactionBranch branch) {
        if (connection == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        try {
            String sql = "INSERT INTO txc_branch " +
                    "(branch_id, tx_id, branch_name, status, error_msg, execute_time, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            pstmt = connection.prepareStatement(sql);
            long now = System.currentTimeMillis();
            
            pstmt.setString(1, branch.getBranchId());
            pstmt.setString(2, branch.getTxId());
            pstmt.setString(3, branch.getBranchName());
            pstmt.setString(4, branch.getStatus().name());
            pstmt.setString(5, branch.getErrorMsg());
            pstmt.setLong(6, branch.getExecuteTime());
            pstmt.setLong(7, now);
            pstmt.setLong(8, now);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to save branch: " + branch.getBranchId(), e);
        } finally {
            closeStatement(pstmt);
        }
    }
    
    /**
     * 更新事务状态
     */
    public void updateTransactionStatus(String txId, TransactionStatus status) {
        if (connection == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        try {
            String sql = "UPDATE txc_transaction SET status = ?, update_time = ? WHERE tx_id = ?";
            
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, status.name());
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, txId);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to update transaction status: " + txId, e);
        } finally {
            closeStatement(pstmt);
        }
    }
    
    /**
     * 更新分支状态
     */
    public void updateBranchStatus(String branchId, BranchStatus status) {
        if (connection == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        try {
            String sql = "UPDATE txc_branch SET status = ?, update_time = ? WHERE branch_id = ?";
            
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, status.name());
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, branchId);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to update branch status: " + branchId, e);
        } finally {
            closeStatement(pstmt);
        }
    }
    
    /**
     * 更新重试次数
     */
    public void updateRetryCount(String txId, int retryCount) {
        if (connection == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        try {
            String sql = "UPDATE txc_transaction SET retry_count = ?, update_time = ? WHERE tx_id = ?";
            
            pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, retryCount);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, txId);
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to update retry count: " + txId, e);
        } finally {
            closeStatement(pstmt);
        }
    }
    
    /**
     * 查询失败的事务（用于补偿重试）
     */
    public List<Transaction> queryFailedTransactions() {
        List<Transaction> result = new ArrayList<Transaction>();
        
        if (connection == null) {
            return result;
        }
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            String sql = "SELECT * FROM txc_transaction WHERE status = ? OR status = ?";
            
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, TransactionStatus.ROLLING_BACK.name());
            pstmt.setString(2, TransactionStatus.FAILED.name());
            
            rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Transaction tx = new Transaction(rs.getString("tx_id"));
                tx.setStatus(TransactionStatus.valueOf(rs.getString("status")));
                tx.setStartTime(rs.getLong("start_time"));
                tx.setEndTime(rs.getLong("end_time"));
                
                // 加载分支信息
                List<TransactionBranch> branches = queryBranches(tx.getTxId());
                tx.setBranches(branches);
                
                result.add(tx);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to query failed transactions", e);
        } finally {
            closeResultSet(rs);
            closeStatement(pstmt);
        }
        
        return result;
    }
    
    /**
     * 查询事务的分支
     */
    private List<TransactionBranch> queryBranches(String txId) {
        List<TransactionBranch> result = new ArrayList<TransactionBranch>();
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            String sql = "SELECT * FROM txc_branch WHERE tx_id = ? ORDER BY branch_id";
            
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, txId);
            
            rs = pstmt.executeQuery();
            
            while (rs.next()) {
                TransactionBranch branch = new TransactionBranch(
                        rs.getString("branch_id"),
                        rs.getString("tx_id"),
                        rs.getString("branch_name")
                );
                branch.setStatus(BranchStatus.valueOf(rs.getString("status")));
                branch.setErrorMsg(rs.getString("error_msg"));
                branch.setExecuteTime(rs.getLong("execute_time"));
                
                result.add(branch);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to query branches for tx: " + txId, e);
        } finally {
            closeResultSet(rs);
            closeStatement(pstmt);
        }
        
        return result;
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("TransactionLog closed");
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
            }
        }
    }
    
    private void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
    
    private void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
