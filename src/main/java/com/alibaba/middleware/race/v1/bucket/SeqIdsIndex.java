package com.alibaba.middleware.race.bucket;

import com.alibaba.middleware.race.util.SearchUtil;
import com.alibaba.middleware.race.util.SerializeUtil;
import com.alibaba.middleware.race.v1.AppConstants;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yefei.yf on 2017/4/4.
 */
public class SeqIdsIndex implements Serializable {

    private List<IdRange> seqIdsIndexInfos;

    public SeqIdsIndex() {
        seqIdsIndexInfos = new ArrayList<>(AppConstants.BUCKET_FILE_NUM);
    }

    public void addSeqIdsIndexInfo(IdRange idRange) {
        seqIdsIndexInfos.add(idRange);
    }

    public static SeqIdsIndex getInstanceFromFile(File file) {
        return (SeqIdsIndex) SerializeUtil.unserializeFromFile(file);
    }

    public void serialize2File(File file) {
        SerializeUtil.serialize2File(this, file);
    }

    public int[] getIndexPosition(long k) {

        IdRange[] idRangeArr = seqIdsIndexInfos.toArray(new IdRange[seqIdsIndexInfos.size()]);
        int i = SearchUtil.searchIdRange(k, idRangeArr);

        List<IdRange> idRangeList = idRangeArr[i].getIdRanges();
        int j = SearchUtil.searchIdRange(k, idRangeList.toArray(new IdRange[idRangeList.size()]));

        int[] position = new int[2];
        position[0] = i;
        position[1] = j;

        return position;
    }

}
