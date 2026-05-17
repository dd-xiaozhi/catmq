package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.store.lock.PutMessageLock;
import com.aoaojiao.catmq.store.lock.PutMessageReentrantLock;
import com.aoaojiao.catmq.store.model.Message;
import com.aoaojiao.catmq.store.util.MMapUtil;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Map;

/**
 * commitLog 文件模型
 *
 * @author DD
 */
public class CommitLog {

    @Getter
    private String topicName;
    private String storePath;
    private MappedByteBuffer mappedByteBuffer;
    private final PutMessageLock putMessageLock = new PutMessageReentrantLock();

    /**
     * 加载指定主题名的 commitLog 文件
     *
     * @param topicName 主题名
     */
    public void loadingFileInMMap(String topicName,
                                  String storePath,
                                  int startOffset,
                                  int maxOffset) throws IOException {
        this.topicName = topicName;
        this.storePath = storePath;
        this.mappedByteBuffer = MMapUtil.createRWMappedByteBuffer(getFilePath(), startOffset, maxOffset);
    }

    /**
     * 获取最新 commitLog 文件路径
     *
     * @return 文件路径
     */
    private String getFilePath() {
        Map<String, CatmqTopicModel> catmqTopicModelMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel catmqTopicModel = catmqTopicModelMap.get(this.topicName);
        if (catmqTopicModel == null) {
            throw new IllegalArgumentException(String.format("topic is valid, topicName: [ %s ]", this.topicName));
        }
        CommitLogModel commitLogModel = catmqTopicModel.getCommitLogModel();
        String filename = commitLogModel.getFilename();
        // 文件路径：store目录 + topicName + 当前写入的文件名
        return storePath + File.separator + this.topicName + File.separator + filename;
    }


    /**
     * 写入消息到 commitLog 文件中
     * 判断 commitLog 文件是否已满，如果已满则需要创建新的 commitLog 文件
     * 写入完整消息格式：消息头 + 消息体
     *
     * @param message 消息模型，commitLog 最小数据单元
     * @return 写入后的物理偏移量
     */
    public long writeContent(Message message) {
        Map<String, CatmqTopicModel> catmqTopicModelMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel catmqTopicModel = catmqTopicModelMap.get(this.topicName);
        if (catmqTopicModel == null) {
            throw new RuntimeException("get catmqTopicModel error");
        }

        CommitLogModel commitLogModel = catmqTopicModel.getCommitLogModel();
        if (commitLogModel == null) {
            throw new RuntimeException("get commitLogModel error");
        }

        // 计算消息总大小（包含消息头）
        byte[] messageBytes = message.convertToBytes();
        int messageSize = messageBytes.length;

        putMessageLock.lock();
        try {
            // 检查是否需要创建新文件（需要在写入前检查）
            checkIsCreateNewCommitLogFile(messageSize, commitLogModel);

            // 记录写入前的偏移量（物理偏移量）
            long physicalOffset = commitLogModel.getOffset().get();

            // 写入完整消息（包含消息头）
            MMapUtil.writeContent(this.mappedByteBuffer, messageBytes);

            // 更新偏移量
            commitLogModel.addOffset(messageSize);

            return physicalOffset;
        } finally {
            putMessageLock.unlock();
        }
    }

    /**
     * 检查旧文件是否已经满，是否需要创建新的文件来存储
     *
     * @param messageSize       消息大小
     * @param commitLogModel commitLogModel
     */
    private void checkIsCreateNewCommitLogFile(int messageSize,
                                               CommitLogModel commitLogModel) {
        int offsetDiff = commitLogModel.offsetDiff();
        if (messageSize > offsetDiff) {
            // 消息大小超过剩余空间，需要创建新文件
            String newCommitLogFilename = getCommitLogNextFilename(commitLogModel.getFilename());
            commitLogModel.setFilename(newCommitLogFilename);
            commitLogModel.getOffset().set(messageSize);
            try {
                this.mappedByteBuffer = MMapUtil.createRWMappedByteBuffer(getFilePath(), 0, commitLogModel.getOffsetLimit());
            } catch (IOException e) {
                throw new RuntimeException("create new commitLog error, topic: " + this.topicName);
            }
        }
    }

    /**
     * 获取下一个 commitLog 文件的文件名
     *
     * @param oldCommitLogFilename 旧 commitLog 文件名
     * @return 新 commitLog 文件名
     */
    private String getCommitLogNextFilename(String oldCommitLogFilename) {
        if (oldCommitLogFilename.length() != 8) {
            throw new IllegalArgumentException("fileName must has 8 chars");
        }
        String newIntFileName = String.valueOf(Integer.parseInt(oldCommitLogFilename) + 1);
        StringBuilder newCommitLogFileName = new StringBuilder();
        // 前面补 0 到 8 位
        for (int i = 0; i < 8 - newIntFileName.length(); i++) {
            newCommitLogFileName.append("0");
        }
        return newCommitLogFileName.append(newIntFileName).toString();
    }

    public byte[] readContent(int offset, int offsetSize) {

        return MMapUtil.readContent(this.mappedByteBuffer, offset, offsetSize);
    }

}
