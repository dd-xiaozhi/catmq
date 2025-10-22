package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.store.lock.PutMessageLock;
import com.aoaojiao.catmq.store.lock.PutMessageReentrantLock;
import com.aoaojiao.catmq.store.model.MessageModel;
import com.aoaojiao.catmq.store.util.MMapUtil;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Map;

/**
 * commitLog 文件模型
 *
 * @author DD
 */
public class CommitLogFileModel {

    private String topicName;
    private String storePath;
    private MappedByteBuffer mappedByteBuffer;
    private final PutMessageLock putMessageLock = new PutMessageReentrantLock();

    /**
     * 加载指定主题名的 commitLog 文件
     *
     * @param topicName
     */
    public void loadingFileInMMap(String topicName,
                                  String storePath,
                                  int startOffset,
                                  int maxOffset) throws IOException {
        this.topicName = topicName;
        this.storePath = storePath;
        this.mappedByteBuffer = MMapUtil.createMappedByteBuffer(getFilePath(), startOffset, maxOffset);
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
     * TODO 目前先只写入消息体
     *
     * @param messageModel 消息模型，commitLog 最小数据单元
     */
    public void writeContent(MessageModel messageModel) {
        Map<String, CatmqTopicModel> catmqTopicModelMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel catmqTopicModel = catmqTopicModelMap.get(this.topicName);
        if (catmqTopicModel == null) {
            throw new RuntimeException("get catmqTopicModel error");
        }

        CommitLogModel commitLogModel = catmqTopicModel.getCommitLogModel();
        if (commitLogModel == null) {
            throw new RuntimeException("get commitLogModel error");
        }

        putMessageLock.lock();
        checkIsCreateNewCommitLogFile(messageModel, commitLogModel);
        MMapUtil.writeContent(this.mappedByteBuffer, messageModel.getContent());
        putMessageLock.unlock();
    }

    /**
     * 检查旧文件是否已经满，是否需要创建新的文件来存储
     *
     * @param messageModel   messageModel
     * @param commitLogModel commitLogModel
     */
    private void checkIsCreateNewCommitLogFile(MessageModel messageModel,
                                               CommitLogModel commitLogModel) {
        int messageSize = messageModel.getContent().length;
        commitLogModel.addOffset(messageSize);
        int offsetDiff = commitLogModel.offsetDiff();
        if (offsetDiff <= 0) {
            // 创建新的 commitLog 文件并映射到 MMap 中
            String newCommitLogFilename = getCommitLogNextFilename(commitLogModel.getFilename());
            commitLogModel.setFilename(newCommitLogFilename);
            commitLogModel.getOffset().set(messageSize);
            try {
                this.mappedByteBuffer = MMapUtil.createMappedByteBuffer(getFilePath(), 0, commitLogModel.getOffsetLimit());
            } catch (IOException e) {
                throw new RuntimeException("create new commitLog error, topic: " + this.topicName);
            }
        }
    }

    /**
     * 获取下一个 commitLog 文件的文件名
     *
     * @param oldCommitLogFilename 旧 commitLog 文件名
     * @return
     */
    private String getCommitLogNextFilename(String oldCommitLogFilename) {
        if (oldCommitLogFilename.length() != 8) {
            throw new IllegalArgumentException("fileName must has 8 chars");
        }
        String newIntFileName = String.valueOf(Integer.valueOf(oldCommitLogFilename) + 1);
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

    public String getTopicName() {
        return this.topicName;
    }
}
