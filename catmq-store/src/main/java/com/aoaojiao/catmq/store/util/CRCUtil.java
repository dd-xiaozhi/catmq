package com.aoaojiao.catmq.store.util;

import java.util.zip.CRC32;

/**
 * CRC 工具类
 *
 * @author DD
 */
public class CRCUtil {

    /**
     * 计算字节数组的 CRC32
     *
     * @param data 字节数组
     * @return CRC32 值
     */
    public static int crc32(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }

    /**
     * 计算字符串的 CRC64
     *
     * @param data 字符串
     * @return CRC64 值
     */
    public static long crc64(String data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        return crc64(data.getBytes());
    }

    /**
     * 计算字节数组的 CRC64
     *
     * @param data 字节数组
     * @return CRC64 值
     */
    public static long crc64(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        long crc = 0x42F0E1EBA9EA3693L;
        for (byte b : data) {
            crc = crc64Table[(int) (crc ^ b) & 0xff] ^ (crc >>> 8);
        }
        return crc;
    }

    /**
     * CRC64 查找表
     */
    private static final long[] crc64Table = new long[256];

    static {
        for (int i = 0; i < 256; i++) {
            long crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) == 1) {
                    crc = (crc >>> 1) ^ 0x42F0E1EBA9EA3693L;
                } else {
                    crc = crc >>> 1;
                }
            }
            crc64Table[i] = crc;
        }
    }
}
