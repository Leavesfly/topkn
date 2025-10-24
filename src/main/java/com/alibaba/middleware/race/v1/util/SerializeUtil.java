package com.alibaba.middleware.race.util;


import com.alibaba.middleware.race.bucket.IndexInfo;

import java.io.*;

/**
 * Created by yefei.yf on 2017/4/1.
 */
public class SerializeUtil {
    /**
     * 将对象序列化成byte数组
     *
     * @param serialObject
     * @return
     */
    public static byte[] object2ByteArray(Serializable serialObject) {

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {

            objectOutputStream.writeObject(serialObject);
            return byteArrayOutputStream.toByteArray();

        } catch (IOException e) {
            return new byte[0];
        }

    }

    /**
     * 将字节数组转化成对象
     *
     * @param byteArray
     * @return
     */
    public static Object byteArray2Object(byte[] byteArray) {

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {

            return objectInputStream.readObject();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * @param serialObject
     * @return
     */
    public static <T extends Serializable> T deepCopyBySerializable(T serialObject) {
        return (T) byteArray2Object(object2ByteArray(serialObject));
    }


    public static void serialize2File(Serializable serialObject, File file) {

        byte[] bytes = SerializeUtil.object2ByteArray(serialObject);
        try {
            FileOutputStream fileOut = new FileOutputStream(file);
            fileOut.write(bytes);
            fileOut.flush();
            fileOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Object unserializeFromFile(File file) {

        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            long size = file.length();
            byte[] bytes = new byte[(int) size];
            bufferedInputStream.read(bytes);
            bufferedInputStream.close();
            return SerializeUtil.byteArray2Object(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

}

