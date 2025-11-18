package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.model.Message;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

/**
 * CommitLog 消息追加处理器
 * 负责对 CommitLog 追加消息与创建
 *
 * @author DD
 */
public class CommitLogAppendHandler {

    private final MessageStoreConfig messageStoreConfig;

    private final static CommitLogManager COMMIT_LOG_FILE_MODE_MANAGER = new CommitLogManager();
    
    public CommitLogAppendHandler(MessageStoreConfig messageStoreConfig) {
        this.messageStoreConfig = messageStoreConfig;
    }

    /**
     * 预加载指定 topic 的 commitLog 文件到内存中
     *
     * @param topicName 主题名
     */
    public void prepareLoadingToMMap(String topicName) throws IOException {
        CommitLog commitLog = new CommitLog();
        commitLog.loadingFileInMMap(topicName, messageStoreConfig.getCommitLogDirPath(),
                0, messageStoreConfig.getMessageCommitLogFileSize());
        COMMIT_LOG_FILE_MODE_MANAGER.put(commitLog);
    }

    /**
     * 顺序追加写入消息到文件中
     *
     * @param topicName 主题名
     * @param content   内容
     */
    public void appendMessage(String topicName, byte[] content) throws ClassNotFoundException, IOException {
        CommitLog commitLog = getCommitLogFileModel(topicName);
        Message message = Message.builder()
                .content(content)
                .size(content.length)
                .build();
        commitLog.writeContent(message);
    }

    /**
     * 读取消息
     *
     * @param topicName   主题名
     * @param startOffset 开始偏移量
     * @param offsetSize  偏移大小
     * @return 消息
     */
    public Message readMessage(String topicName, int startOffset, int offsetSize) throws ClassNotFoundException {
        CommitLog commitLog = getCommitLogFileModel(topicName);
        byte[] content = commitLog.readContent(startOffset, offsetSize);
        System.out.println(new String(content));
        return Message.builder().content(content).build();
    }

    private CommitLog getCommitLogFileModel(String topicName) throws ClassNotFoundException {
        if (StringUtils.isBlank(topicName)) {
            throw new IllegalStateException("topic name is blank");
        }

        CommitLog commitLog = COMMIT_LOG_FILE_MODE_MANAGER.get(topicName);
        if (commitLog == null) {
            throw new ClassNotFoundException(String.format("topic: %s is not prepare", topicName));
        }
        return commitLog;
    }

}
