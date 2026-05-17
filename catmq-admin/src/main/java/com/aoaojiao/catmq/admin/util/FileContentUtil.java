package com.aoaojiao.catmq.admin.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件内容工具类
 *
 * @author DD
 */
public class FileContentUtil {

    /**
     * 读取文件内容为字符串
     */
    public static String readFileTpString(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 写入字符串到文件
     */
    public static void writeStringToFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        // 确保父目录存在
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}