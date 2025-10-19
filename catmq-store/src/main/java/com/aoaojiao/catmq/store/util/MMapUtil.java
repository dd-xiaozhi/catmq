package com.aoaojiao.catmq.store.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * @author DD
 */
public class MMapUtil {

    /**
     * 指定 offset 创建文件映射
     *
     * @param filePath    文件路径
     * @param startOffset 映射开始位置
     * @param mappedSize  映射大小
     * @return MappedByteBuffer
     * @throws IOException
     */
    public static MappedByteBuffer createMappedByteBuffer(String filePath,
                                                          int startOffset,
                                                          int mappedSize) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("file path [ " + filePath + " ] is valid");
        }
        FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        MappedByteBuffer mappedByteBuffer = fileChannel
                .map(FileChannel.MapMode.READ_WRITE, startOffset, mappedSize);
        return mappedByteBuffer;
    }


    /**
     * 从指定的 offset 读取指定大小的内容
     *
     * @param readOffset 读取的 offset
     * @param size       读取大小
     * @return content
     */
    public static byte[] readContent(MappedByteBuffer mappedByteBuffer,
                                     int readOffset,
                                     int size) {
        // 将读取指针移动到指定 offset
        mappedByteBuffer.position(readOffset);
        byte[] buffer = new byte[size];
        int readIndex = 0;
        for (int i = 0; i < size; i++) {
            buffer[readIndex++] = mappedByteBuffer.get(readOffset + i);
        }
        return buffer;
    }

    public static void writeContent(MappedByteBuffer mappedByteBuffer,
                                    byte[] content) {
        writeContent(mappedByteBuffer, content, false);
    }

    /**
     * 写入内容到磁盘中
     *
     * @param content 写入内容
     * @param force   是否强制刷盘，不强制刷盘的话数据会先写入 pageCache 中，再由操作系统刷盘
     */
    public static void writeContent(MappedByteBuffer mappedByteBuffer,
                                    byte[] content,
                                    boolean force) {
        mappedByteBuffer.put(content);
        if (force) {
            // 强制刷盘
            mappedByteBuffer.force();
        }
    }

    /**
     * 释放 MMap 内存
     * 注意：MappedByteBuffer::clear() 不会释放内存，而是清除缓冲区，内存还是占用的
     * 因为 MMap 申请的是堆外内存，不归JVM虚拟机管理，所以不会被 GC，需要我们手动释放内存
     *
     * @param mappedByteBuffer
     */
    public static void clean(MappedByteBuffer mappedByteBuffer) {
        if (mappedByteBuffer == null || !mappedByteBuffer.isDirect() || mappedByteBuffer.capacity() == 0)
            return;
        invoke(invoke(viewed(mappedByteBuffer), "cleaner"), "clean");
    }

    private static Object invoke(final Object target, final String methodName, final Class<?>... args) {
        return AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            try {
                Method method = method(target, methodName, args);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Method method(Object target, String methodName, Class<?>[] args)
            throws NoSuchMethodException {
        try {
            return target.getClass().getMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            return target.getClass().getDeclaredMethod(methodName, args);
        }
    }

    private static ByteBuffer viewed(ByteBuffer buffer) {
        String methodName = "viewedBuffer";
        Method[] methods = buffer.getClass().getMethods();
        // 兼容 jdk 版本
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals("attachment")) {
                methodName = "attachment";
                break;
            }
        }

        ByteBuffer viewedBuffer = (ByteBuffer) invoke(buffer, methodName);
        if (viewedBuffer == null)
            return buffer;
        else
            return viewed(viewedBuffer);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        MappedByteBuffer mappedByteBuffer = createMappedByteBuffer("D:\\Work\\Java\\study\\study\\netty-study\\src\\main\\java\\com\\xiaozhi\\study\\utils\\test",
                0, 1 * 1024 * 1024);
        byte[] bytes = "i am xiaozhi".getBytes();
        writeContent(mappedByteBuffer, bytes, true);
        System.out.println(new String(readContent(mappedByteBuffer, 0, bytes.length)));
    }
}
