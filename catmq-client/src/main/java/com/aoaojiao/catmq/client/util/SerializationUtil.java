package com.aoaojiao.catmq.client.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 序列化工具类
 *
 * @author DD
 */
public class SerializationUtil {

    private static final Logger log = LoggerFactory.getLogger(SerializationUtil.class);

    /**
     * 序列化为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj);
    }

    /**
     * 序列化为字节数组
     *
     * @param obj 对象
     * @return 字节数组
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        return JSON.toJSONBytes(obj);
    }

    /**
     * 从 JSON 字符串反序列化
     *
     * @param json    JSON 字符串
     * @param clazz   类型
     * @param <T>     泛型
     * @return 对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    /**
     * 从字节数组反序列化
     *
     * @param data  字节数组
     * @param clazz 类型
     * @param <T>   泛型
     * @return 对象
     */
    public static <T> T fromJson(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }
        return JSON.parseObject(data, clazz);
    }

    /**
     * 从字节数组反序列化（带泛型）
     *
     * @param data        字节数组
     * @param typeRef     类型引用
     * @param <T>         泛型
     * @return 对象
     */
    public static <T> T fromJson(byte[] data, com.alibaba.fastjson2.TypeReference<T> typeRef) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        return JSON.parseObject(json, typeRef);
    }

    /**
     * 判断是否为 JSON 字符串
     *
     * @param str 字符串
     * @return 是否为 JSON
     */
    public static boolean isJson(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            JSON.parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}