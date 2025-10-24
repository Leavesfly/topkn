package io.leavesfly.middleware.race.v1.bucket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.leavesfly.middleware.race.v1.AppConstants;

/**
 * Created by yefei.yf on 2017/4/2.
 */
public class IndexInfo implements Serializable {

    private Map<Integer, InnerBucketInfo> indexMapInfo;

    public IndexInfo() {
        indexMapInfo = new ConcurrentHashMap<>();
    }

    public Map<Integer, InnerBucketInfo> getIndexMapInfo() {
        return indexMapInfo;
    }

    public InnerBucketInfo getInnerBucketInfo(int key) {
        return indexMapInfo.get(key);

    }

    public long getIndexInfoHoldValueSize() {
        long result = 0l;
        for (InnerBucketInfo innerBucketInfo : indexMapInfo.values()) {
            result += innerBucketInfo.getHoldValueNum().get();
        }
        return result;
    }


    public void addInnerBucketInfo(int innerBucketIndex, InnerBucketInfo innerBucketInfo) {
        indexMapInfo.put(innerBucketIndex, innerBucketInfo);

    }

    /**
     * 构建索引的数值下标范围
     *
     * @param beginIdIndex
     */
    public IdRange buildSeqIdsIndexInfo(long beginIdIndex) {

        List<IdRange> idRanges = new ArrayList<>(AppConstants.FILE_INNER_BUCKET_NUM);

        InnerBucketInfo innerBucketInfo = null;
        IdRange idRangeTmp = null;
        int flagIndex = 0;
        for (int i = 0; i < AppConstants.FILE_INNER_BUCKET_NUM; i++) {
            innerBucketInfo = indexMapInfo.get(i);
            if (innerBucketInfo != null) {
                flagIndex = i;
                idRangeTmp = new IdRange(beginIdIndex, beginIdIndex + innerBucketInfo.getHoldValueNum().get() - 1);
                idRanges.add(idRangeTmp);
                innerBucketInfo.setBeginIdIndexValue(idRangeTmp.getBeingSeqIdValue());
                innerBucketInfo.setEndIdIndexValue(idRangeTmp.getEndSeqIdValue());
                break;
            }
        }


        for (int i = flagIndex + 1; i < AppConstants.FILE_INNER_BUCKET_NUM; i++) {
            innerBucketInfo = null;
            innerBucketInfo = indexMapInfo.get(i);
            if (innerBucketInfo != null) {
                idRangeTmp = new IdRange(idRangeTmp.getEndSeqIdValue() + 1,
                        idRangeTmp.getEndSeqIdValue() + innerBucketInfo.getHoldValueNum().get());
                idRanges.add(idRangeTmp);
                innerBucketInfo.setBeginIdIndexValue(idRangeTmp.getBeingSeqIdValue());
                innerBucketInfo.setEndIdIndexValue(idRangeTmp.getEndSeqIdValue());
            }

        }

        IdRange result = new IdRange(beginIdIndex, idRangeTmp.getEndSeqIdValue());
        result.setIdRanges(idRanges);
        return result;
    }

}
