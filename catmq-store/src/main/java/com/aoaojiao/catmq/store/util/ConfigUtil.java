package com.aoaojiao.catmq.store.util;

import lombok.experimental.UtilityClass;

/**
 * 配置读取工具类
 * <p>
 * 支持多数据来源优先级（从高到低）：
 * 1. 系统属性（-Dkey=value）
 * 2. 环境变量
 * 3. 代码默认值
 *
 * @author DD
 */
@UtilityClass
public class ConfigUtil {

    /**
     * 系统属性前缀
     */
    private static final String SYSTEM_PROPERTY_PREFIX = "catmq.store.";

    /**
     * 环境变量前缀
     */
    private static final String ENV_PREFIX = "CATMQ_STORE_";

    /**
     * 从系统属性、环境变量或默认值中获取字符串配置
     *
     * @param configKey    配置项名称（不含前缀）
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static String getString(String configKey, String defaultValue) {
        // 1. 尝试从系统属性获取
        String systemValue = System.getProperty(SYSTEM_PROPERTY_PREFIX + configKey);
        if (systemValue != null && !systemValue.isEmpty()) {
            return systemValue;
        }

        // 2. 尝试从环境变量获取
        // 环境变量名转换：将 configKey 中的点号替换为下划线，全大写
        String envKey = ENV_PREFIX + configKey.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // 3. 返回默认值
        return defaultValue;
    }

    /**
     * 从系统属性、环境变量或默认值中获取整数配置
     *
     * @param configKey    配置项名称（不含前缀）
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getInt(String configKey, int defaultValue) {
        // 1. 尝试从系统属性获取
        String systemValue = System.getProperty(SYSTEM_PROPERTY_PREFIX + configKey);
        if (systemValue != null && !systemValue.isEmpty()) {
            try {
                return Integer.parseInt(systemValue);
            } catch (NumberFormatException e) {
                // 忽略解析错误，使用默认值
            }
        }

        // 2. 尝试从环境变量获取
        String envKey = ENV_PREFIX + configKey.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // 忽略解析错误，使用默认值
            }
        }

        // 3. 返回默认值
        return defaultValue;
    }

    /**
     * 从系统属性、环境变量或默认值中获取长整数配置
     *
     * @param configKey    配置项名称（不含前缀）
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static long getLong(String configKey, long defaultValue) {
        // 1. 尝试从系统属性获取
        String systemValue = System.getProperty(SYSTEM_PROPERTY_PREFIX + configKey);
        if (systemValue != null && !systemValue.isEmpty()) {
            try {
                return Long.parseLong(systemValue);
            } catch (NumberFormatException e) {
                // 忽略解析错误，使用默认值
            }
        }

        // 2. 尝试从环境变量获取
        String envKey = ENV_PREFIX + configKey.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Long.parseLong(envValue);
            } catch (NumberFormatException e) {
                // 忽略解析错误，使用默认值
            }
        }

        // 3. 返回默认值
        return defaultValue;
    }

    /**
     * 从系统属性、环境变量或默认值中获取布尔配置
     *
     * @param configKey    配置项名称（不含前缀）
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static boolean getBoolean(String configKey, boolean defaultValue) {
        // 1. 尝试从系统属性获取
        String systemValue = System.getProperty(SYSTEM_PROPERTY_PREFIX + configKey);
        if (systemValue != null && !systemValue.isEmpty()) {
            return Boolean.parseBoolean(systemValue);
        }

        // 2. 尝试从环境变量获取
        String envKey = ENV_PREFIX + configKey.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return Boolean.parseBoolean(envValue);
        }

        // 3. 返回默认值
        return defaultValue;
    }
}