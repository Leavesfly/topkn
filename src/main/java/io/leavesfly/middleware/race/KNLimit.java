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


    // 读取数据文件的目录(比赛时候使用)
//    String DATA_DIR = "/home/admin/topkn-datafiles/";
//    String RESULT_DIR = "/home/admin/topkn-resultfiles/69820fqab7/";
//    String MIDDLE_DIR = "/home/admin/middle/69820fqab7/";
//    String LOG_FILE = "/home/admin/logs/69820fqab7/teser-69820fqab7-INFO.log";

    // 测试的时候可以换成自己的目录(测试使用)
    String DATA_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/source_data/";
    String RESULT_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/result_data/";
    String MIDDLE_DIR = "/Users/yefei.yf/Projects/LimitKNDemo/middle_data/";
    String LOG_FILE = "/Users/yefei.yf/Projects/limitkn.log";

    /**
     * @param k
     * @param n
     */
    void processTopKN(long k, int n);

}
