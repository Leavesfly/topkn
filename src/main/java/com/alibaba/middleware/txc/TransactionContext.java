package com.alibaba.middleware.txc;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务上下文，用于在事务执行过程中传递参数和结果
 */
public class TransactionContext {
    
    // 事务ID
    private String txId;
    
    // 上下文数据
    private ConcurrentHashMap<String, Object> contextData;
    
    public TransactionContext(String txId) {
        this.txId = txId;
        this.contextData = new ConcurrentHashMap<String, Object>();
    }
    
    public String getTxId() {
        return txId;
    }
    
    public void put(String key, Object value) {
        contextData.put(key, value);
    }
    
    public Object get(String key) {
        return contextData.get(key);
    }
    
    public void remove(String key) {
        contextData.remove(key);
    }
    
    public boolean containsKey(String key) {
        return contextData.containsKey(key);
    }
}
