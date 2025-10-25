package io.leavesfly.middleware.race;


/**
 * Created by wanshao on 2017/3/16.
 *
 * @author wanshao
 * @date 2017/03/16
 */
public interface KNLimit {

    // 数据文件的命名前缀
    String FILE_PREFIX = "KNLIMIT_";
    // 数据文件的命名后缀
    String FILE_SUFFIX = ".data";

    // 结果文件的命名
    String RESULT_NAME = "RESULT.rs";


    // 测试的时候可以换成自己的目录(测试使用)
    String SOURCE_DATA_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/source_data/";

    String MIDDLE_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/middle_data/";

    String LOG_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/log/";

    String RESULT_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/result_data/";


    static String getSourceDataFileName(int fileId) {
        return KNLimit.SOURCE_DATA_DIR + KNLimit.FILE_PREFIX + fileId + KNLimit.FILE_SUFFIX;
    }

    /**
     * @param k
     * @param n
     */
    void processTopKN(long k, int n);

}
