package com.aoaojiao.catmq.broker.utils;

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
    
    public static void writeStringToFile(String content, String filePath) throws IOException {
        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(content);
        }
    }
}
