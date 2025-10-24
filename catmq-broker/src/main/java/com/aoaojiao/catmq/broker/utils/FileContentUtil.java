package com.aoaojiao.catmq.broker.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 文件内容工具
 *
 * @author DD
 */
public class FileContentUtil {

    public static String readFileTpString(String filePath) throws IOException {
        checkFilePath(filePath);
        try (FileReader fr = new FileReader(filePath)) {
            int len;
            char[] buffer = new char[1024];
            StringBuilder str = new StringBuilder();
            while ((len = fr.read(buffer)) != -1) {
                str.append(buffer, 0, len);
            }
            return str.toString();
        }
    }
    
    public static void writeStringToFile(String filePath, String content) throws IOException {
        checkFilePath(filePath);
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("file content is blank");
        }
        
        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(content);
        }
    }
    
    private static void checkFilePath(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            throw new IllegalArgumentException("file path is blank");
        }
    }
}
