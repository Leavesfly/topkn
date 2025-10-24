package com.alibaba.middleware.race.bucket;

import java.io.Serializable;
import java.util.List;

/**
 * Created by yefei.yf on 2017/4/4.
 */
public class IdRange implements Serializable {

    private long beingSeqIdValue;
    private long endSeqIdValue;

    private List<IdRange> idRanges;

    public IdRange(long beginIdIndex, long endIdIndex) {
        beingSeqIdValue = beginIdIndex;
        endSeqIdValue = endIdIndex;
    }

    public long getBeingSeqIdValue() {
        return beingSeqIdValue;
    }

    public long getEndSeqIdValue() {
        return endSeqIdValue;
    }

    public List<IdRange> getIdRanges() {
        return idRanges;
    }

    public void setIdRanges(List<IdRange> idRanges) {
        this.idRanges = idRanges;
    }

    public boolean isIn(long value) {
        return value >= beingSeqIdValue && value <= endSeqIdValue;
    }

    public boolean isLeft(long value) {
        return value < beingSeqIdValue;
    }

    public boolean isRight(long value) {
        return value > endSeqIdValue;

    }
}