package com.alibaba.middleware.race.bucket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by yefei.yf on 2017/4/2.
 */
public class InnerBucketInfo implements Serializable {

    private List<Long> pageIndexes;
    private AtomicLong holdValueNum;

    private long beginIdIndexValue;
    private long endIdIndexValue;


    public InnerBucketInfo() {
        pageIndexes = Collections.synchronizedList(new LinkedList<Long>());
        holdValueNum = new AtomicLong(0);
    }

    public void add(long pageIndex, int addNum) {
        pageIndexes.add(pageIndex);
        holdValueNum.addAndGet(addNum);
    }

    public AtomicLong getHoldValueNum() {
        return holdValueNum;
    }

    public List<Long> getPageIndexes() {
        return pageIndexes;
    }


    public long getBeginIdIndexValue() {
        return beginIdIndexValue;
    }

    public void setBeginIdIndexValue(long beginIdIndexValue) {
        this.beginIdIndexValue = beginIdIndexValue;
    }

    public long getEndIdIndexValue() {
        return endIdIndexValue;
    }

    public void setEndIdIndexValue(long endIdIndexValue) {
        this.endIdIndexValue = endIdIndexValue;
    }
}
