package io.leavesfly.middleware.race;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * 生成10个文件，每个文件大小为1G Created by wanshao on 2017/3/16.
 *
 * @author wanshao
 * @date 2017/03/16
 */
public class DataGenerator {

    // 每个小文件的大小，比赛规定是每个文件大小1G。
    private static final long FILE_SIZE = 1 * 1024 * 1024 * 1024L;

    public static void main(String[] args) throws IOException {

        Random random = new Random(System.currentTimeMillis());
        File fileDir = new File(KNLimit.DATA_DIR);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        for (int i = 0; i < 10; i++) {
            // 写出的文件名
            String fileName = KNLimit.FILE_PREFIX + i + KNLimit.FILE_SUFFIX;
            File file = new File(KNLimit.DATA_DIR + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file);
            while (file.length() < FILE_SIZE) {
                Long randomValue = Math.abs(random.nextLong());
                fileWriter.append(String.valueOf(randomValue) + "\n");
            }

            fileWriter.close();
        }
    }

    public static void geneData() {

        File sourceDataDir = new File(KNLimit.DATA_DIR);
        if (sourceDataDir.listFiles().length > 0) {
            return;
        }

        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < 10; i++) {
            // 写出的文件名
            String fileName = KNLimit.FILE_PREFIX + i + KNLimit.FILE_SUFFIX;
            File file = new File(KNLimit.DATA_DIR + fileName);
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(file);
                while (file.length() < FILE_SIZE) {
                    Long randomValue = Math.abs(random.nextLong());
                    fileWriter.append(String.valueOf(randomValue) + "\n");
                }
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
