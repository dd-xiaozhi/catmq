package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.model.AppendResult;
import com.aoaojiao.catmq.store.model.DispatchRequest;
import com.aoaojiao.catmq.store.model.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * CommitLog 消息追加处理器
 * 负责对 CommitLog 追加消息与创建
 *
 * @author DD
 */
public class CommitLogAppendHandler {

    private static final Logger log = LoggerFactory.getLogger(CommitLogAppendHandler.class);

    private final MessageStoreConfig messageStoreConfig;

    private final static CommitLogManager COMMIT_LOG_FILE_MODE_MANAGER = new CommitLogManager();

    /**
     * 消息分发服务（可选，如果未设置则不进行分发）
     */
    private DispatchMessageService dispatchMessageService;

    public CommitLogAppendHandler(MessageStoreConfig messageStoreConfig) {
        this.messageStoreConfig = messageStoreConfig;
    }

    /**
     * 设置分发服务
     *
     * @param dispatchMessageService 分发服务
     */
    public void setDispatchMessageService(DispatchMessageService dispatchMessageService) {
        this.dispatchMessageService = dispatchMessageService;
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
     * 顺序追加写入消息到文件中（旧API，保持兼容）
     *
     * @param topicName 主题名
     * @param content   内容
     */
    public void appendMessage(String topicName, byte[] content) throws ClassNotFoundException, IOException {
        appendMessage(topicName, 0, content);
    }

    /**
     * 顺序追加写入消息到文件中
     *
     * @param topicName 主题名
     * @param queueId   队列 ID
     * @param content   内容
     */
    public void appendMessage(String topicName, int queueId, byte[] content) throws ClassNotFoundException, IOException {
        // 创建简单消息
        Message message = Message.createSimpleMessage(content);

        // 调用新的追加方法
        AppendResult result = appendMessage(topicName, queueId, message);

        if (!result.isSuccess()) {
            throw new IOException("Append message failed: " + result.getErrorMsg());
        }
    }

    /**
     * 追加消息到 CommitLog（完整实现，包含消息头）
     *
     * @param topicName 主题名
     * @param queueId   队列 ID
     * @param message   消息
     * @return 追加结果
     */
    public AppendResult appendMessage(String topicName, int queueId, Message message) {
        try {
            // 获取 CommitLog
            CommitLog commitLog = getCommitLogFileModel(topicName);

            // 初始化消息默认值
            message.initDefaultValues();
            message.setQueueId(queueId);
            message.setTimestamp(System.currentTimeMillis());

            // 写入 CommitLog，获取物理偏移量
            long physicalOffset = commitLog.writeContent(message);
            message.setPhysicalOffset(physicalOffset);

            // 异步分发到 ConsumerQueue
            if (dispatchMessageService != null) {
                DispatchRequest request = DispatchRequest.builder()
                        .topic(topicName)
                        .queueId(queueId)
                        .physicalOffset(physicalOffset)
                        .size(message.getTotalSize())
                        .tagCode(message.getTagCode())
                        .timestamp(message.getTimestamp())
                        .build();

                dispatchMessageService.putDispatchRequest(request);

                log.debug("Message appended and dispatched: topic={}, queueId={}, offset={}, size={}",
                        topicName, queueId, physicalOffset, message.getTotalSize());
            }

            return AppendResult.success(physicalOffset, message.getTotalSize());

        } catch (ClassNotFoundException e) {
            log.error("Append message error: topic={}, queueId={}", topicName, queueId, e);
            return AppendResult.fail("Topic not found: " + topicName);
        } catch (Exception e) {
            log.error("Append message error: topic={}, queueId={}", topicName, queueId, e);
            return AppendResult.fail(e.getMessage());
        }
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
        return Message.parseFrom(content);
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

    /**
     * 获取 CommitLogManager
     *
     * @return CommitLogManager
     */
    public static CommitLogManager getCommitLogManager() {
        return COMMIT_LOG_FILE_MODE_MANAGER;
    }
}
