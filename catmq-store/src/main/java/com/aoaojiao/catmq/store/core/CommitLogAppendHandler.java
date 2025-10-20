package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.constants.StoreConstant;
import com.aoaojiao.catmq.store.model.MessageModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

/**
 * CommitLog 消息追加处理器
 * 负责对 CommitLog 追加消息与创建
 *
 * @author DD
 */
public class CommitLogAppendHandler {

    private final static CommitLogFileModeManager COMMIT_LOG_FILE_MODE_MANAGER = new CommitLogFileModeManager();
    
    private MessageStoreConfig messageStoreConfig;

    /**
     * 预加载指定 topic 的 commitLog 文件到内存中
     *
     * @param topicName
     */
    public void prepareLoadingToMMap(String topicName) throws IOException {
        CommitLogFileModel commitLogFileModel = new CommitLogFileModel();
        commitLogFileModel.loadingFileInMMap(topicName, 0, StoreConstant.COMMIT_LOG_DEFAULT_SIZE);
        COMMIT_LOG_FILE_MODE_MANAGER.put(commitLogFileModel);
    }

    /**
     * 顺序追加写入消息到文件中
     *
     * @param topicName 主题名
     * @param content   内容
     */
    public void appendMessage(String topicName, byte[] content) throws ClassNotFoundException {
        CommitLogFileModel commitLogFileModel = getCommitLogFileModel(topicName);
        MessageModel messageModel = MessageModel.builder()
                .content(content)
                .size(content.length)
                .build();
        commitLogFileModel.writeContent(messageModel);
    }

    /**
     * 读取消息
     *
     * @param topicName   主题名
     * @param startOffset 开始偏移量
     * @param offsetSize  偏移大小
     * @return 消息
     */
    public MessageModel readMessage(String topicName, int startOffset, int offsetSize) throws ClassNotFoundException {
        CommitLogFileModel commitLogFileModel = getCommitLogFileModel(topicName);
        byte[] content = commitLogFileModel.readContent(startOffset, offsetSize);
        System.out.println(new String(content));
        return null;
    }

    private CommitLogFileModel getCommitLogFileModel(String topicName) throws ClassNotFoundException {
        if (StringUtils.isBlank(topicName)) {
            throw new IllegalStateException("topic name is blank");
        }

        CommitLogFileModel commitLogFileModel = COMMIT_LOG_FILE_MODE_MANAGER.get(topicName);
        if (commitLogFileModel == null) {
            throw new ClassNotFoundException(String.format("topic: %s is not prepare", topicName));
        }
        return commitLogFileModel;
    }

}
