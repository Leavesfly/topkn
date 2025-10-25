package io.leavesfly.middleware.race;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Random;

/**
 * 生成10个文件，每个文件大小为1G Created by wanshao on 2017/3/16.
 *
 * @author wanshao
 * @date 2017/03/16
 */
public class DataGenerator {

    // 每个小文件的大小，比赛规定是每个文件大小1G，本地测试改成每个文件100M。
    private static final long FILE_SIZE = (long) (0.1f * 1024 * 1024 * 1024L);

    public static void main(String[] args) throws IOException {

        geneData();
    }

    public static void geneData() {

        File sourceDataDir = new File(KNLimit.SOURCE_DATA_DIR);
        sourceDataDir.mkdirs();

        if (Objects.requireNonNull(sourceDataDir.listFiles()).length > 0) {
            return;
        }

        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < 10; i++) {
            String fileName = KNLimit.getSourceDataFileName(i);
            File file = new File(fileName);
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

            System.out.println(fileName + "had Gened.");
        }
    }
}
