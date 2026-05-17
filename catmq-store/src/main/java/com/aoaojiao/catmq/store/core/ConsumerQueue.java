package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.store.constants.StoreConstant;
import com.aoaojiao.catmq.store.lock.PutMessageLock;
import com.aoaojiao.catmq.store.lock.PutMessageReentrantLock;
import com.aoaojiao.catmq.store.model.CQIndex;
import com.aoaojiao.catmq.store.util.MMapUtil;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消费队列索引文件
 * <p>
 * 文件路径：consumeQueue/{topic}/{queueId}/00000000
 * 每个条目固定 20 字节：physicalOffset(8) + size(4) + tagCode(8)
 *
 * @author DD
 */
public class ConsumerQueue {

    /**
     * 每条索引的大小：8 + 4 + 8 = 20 字节
     */
    public static final int CQ_INDEX_SIZE = StoreConstant.CQ_INDEX_SIZE;

    /**
     * 主题
     */
    @Getter
    private String topic;

    /**
     * 队列 ID
     */
    @Getter
    private int queueId;

    /**
     * 存储路径
     */
    private String storePath;

    /**
     * MMap 缓冲区
     */
    @Getter
    private MappedByteBuffer mappedByteBuffer;

    /**
     * 最大索引条目数
     */
    private int maxIndexCount;

    /**
     * 当前索引位置
     */
    private AtomicInteger currentIndex;

    /**
     * 写入锁
     */
    private final PutMessageLock lock = new PutMessageReentrantLock();

    /**
     * 文件夹路径
     */
    private String folderPath;

    /**
     * 默认构造函数
     */
    public ConsumerQueue() {
        this.currentIndex = new AtomicInteger(0);
    }

    /**
     * 加载 ConsumerQueue 文件到 MMap
     *
     * @param topic     主题
     * @param queueId   队列 ID
     * @param storePath 存储路径
     * @param fileSize  文件大小
     */
    public void loadingFileInMMap(String topic,
                                  int queueId,
                                  String storePath,
                                  int fileSize) throws IOException {
        this.topic = topic;
        this.queueId = queueId;
        this.storePath = storePath;
        this.maxIndexCount = fileSize / CQ_INDEX_SIZE;
        this.currentIndex = new AtomicInteger(0);

        // 创建文件夹
        this.folderPath = storePath + File.separator + topic + File.separator + queueId;
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // 获取文件路径
        String filePath = getFilePath();

        // 创建/加载文件
        this.mappedByteBuffer = MMapUtil.createRWMappedByteBuffer(filePath, 0, fileSize);
    }

    /**
     * 获取文件路径
     */
    private String getFilePath() {
        // 检查当前索引位置，计算文件序号
        int fileSeq = currentIndex.get() / maxIndexCount;
        String filename = String.format("%08d", fileSeq);
        return folderPath + File.separator + filename;
    }

    /**
     * 获取当前文件名
     */
    private String getCurrentFilename() {
        int fileSeq = currentIndex.get() / maxIndexCount;
        return String.format("%08d", fileSeq);
    }

    /**
     * 写入索引（分发消息时调用）
     *
     * @param physicalOffset CommitLog 物理偏移量
     * @param size           消息大小
     * @param tagCode        标签哈希
     */
    public void writeIndex(long physicalOffset, int size, long tagCode) {
        lock.lock();
        try {
            int pos = currentIndex.get() * CQ_INDEX_SIZE;

            // 写入 physicalOffset（8 字节）
            mappedByteBuffer.putLong(pos, physicalOffset);

            // 写入 size（4 字节）
            mappedByteBuffer.putInt(pos + 8, size);

            // 写入 tagCode（8 字节）
            mappedByteBuffer.putLong(pos + 12, tagCode);

            currentIndex.incrementAndGet();

            // 检查是否需要换文件
            if (currentIndex.get() >= maxIndexCount) {
                rollToNextFile();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取索引（消费者拉取时调用）
     *
     * @param logicOffset 逻辑偏移量
     * @return 索引信息
     */
    public CQIndex getIndex(long logicOffset) {
        int indexPos = (int) logicOffset;
        if (indexPos < 0 || indexPos >= currentIndex.get()) {
            throw new IndexOutOfBoundsException(
                    String.format("Invalid logic offset: %d, current max: %d", logicOffset, currentIndex.get() - 1));
        }

        // 计算所在的文件
        int fileSeq = indexPos / maxIndexCount;
        int indexInFile = indexPos % maxIndexCount;
        int pos = indexInFile * CQ_INDEX_SIZE;

        // 获取对应文件的缓冲区
        MappedByteBuffer buffer = getBuffer(fileSeq);
        if (buffer == null) {
            throw new RuntimeException("Cannot get buffer for file seq: " + fileSeq);
        }

        long physicalOffset = buffer.getLong(pos);
        int size = buffer.getInt(pos + 8);
        long tagCode = buffer.getLong(pos + 12);

        return new CQIndex(physicalOffset, size, tagCode);
    }

    /**
     * 获取指定文件序号的缓冲区
     */
    private MappedByteBuffer getBuffer(int fileSeq) {
        String filename = String.format("%08d", fileSeq);
        String filePath = folderPath + File.separator + filename;

        try {
            // 如果是当前文件，直接返回
            if (fileSeq == currentIndex.get() / maxIndexCount) {
                return mappedByteBuffer;
            }

            // 加载其他文件
            return MMapUtil.createRWMappedByteBuffer(filePath, 0, maxIndexCount * CQ_INDEX_SIZE);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 获取最大逻辑偏移
     */
    public long getMaxLogicOffset() {
        return currentIndex.get() - 1;
    }

    /**
     * 获取最小逻辑偏移
     */
    public long getMinLogicOffset() {
        return 0;
    }

    /**
     * 获取当前索引数量
     */
    public int getIndexCount() {
        return currentIndex.get();
    }

    /**
     * 获取最大条目数
     */
    public int getMaxIndexCount() {
        return maxIndexCount;
    }

    /**
     * 切换到下一个文件
     */
    private void rollToNextFile() {
        // 刷盘当前文件
        if (mappedByteBuffer != null) {
            mappedByteBuffer.force();
        }

        // 重置索引位置
        currentIndex.set(0);

        // 获取新文件名
        int nextFileSeq = (currentIndex.get() / maxIndexCount) + 1;
        String newFilename = String.format("%08d", nextFileSeq);
        String newFilePath = folderPath + File.separator + newFilename;

        try {
            // 创建新文件并映射
            this.mappedByteBuffer = MMapUtil.createRWMappedByteBuffer(newFilePath, 0, maxIndexCount * CQ_INDEX_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("Roll ConsumerQueue file error", e);
        }
    }

    /**
     * 检查是否有新消息
     *
     * @param currentOffset 当前消费偏移
     * @return 是否有新消息
     */
    public boolean hasNewMessage(long currentOffset) {
        return currentOffset <= getMaxLogicOffset();
    }

    /**
     * 释放资源
     */
    public void shutdown() {
        if (mappedByteBuffer != null) {
            MMapUtil.clean(mappedByteBuffer);
            mappedByteBuffer = null;
        }
    }

    @Override
    public String toString() {
        return "ConsumerQueue{" +
                "topic='" + topic + '\'' +
                ", queueId=" + queueId +
                ", currentIndex=" + currentIndex.get() +
                ", maxIndexCount=" + maxIndexCount +
                '}';
    }
}
