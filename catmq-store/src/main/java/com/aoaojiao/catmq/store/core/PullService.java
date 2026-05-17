package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.model.CQIndex;
import com.aoaojiao.catmq.store.model.Message;
import com.aoaojiao.catmq.store.model.PullResult;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息拉取服务
 *
 * @author DD
 */
public class PullService {

    private static final Logger log = LoggerFactory.getLogger(PullService.class);

    private final MessageStoreConfig config;
    private final ConsumerQueueManager consumerQueueManager;
    private final CommitLogManager commitLogManager;

    /**
     * 默认最大拉取消息数
     */
    private static final int DEFAULT_MAX_MSG_COUNT = 32;

    /**
     * 默认最大拉取消息大小 (4MB)
     */
    private static final int DEFAULT_MAX_MSG_SIZE = 4 * 1024 * 1024;

    public PullService(MessageStoreConfig config,
                       ConsumerQueueManager consumerQueueManager,
                       CommitLogManager commitLogManager) {
        this.config = config;
        this.consumerQueueManager = consumerQueueManager;
        this.commitLogManager = commitLogManager;
    }

    /**
     * 拉取消息
     *
     * @param topic        主题
     * @param queueId      队列 ID
     * @param offset       拉取起始偏移
     * @param maxMsgCount  最大消息数量
     * @param maxMsgSize   最大消息大小
     * @return 拉取结果
     */
    public PullResult pull(String topic,
                          int queueId,
                          long offset,
                          int maxMsgCount,
                          int maxMsgSize) {

        // 参数校验
        if (maxMsgCount <= 0) {
            maxMsgCount = DEFAULT_MAX_MSG_COUNT;
        }
        if (maxMsgSize <= 0) {
            maxMsgSize = DEFAULT_MAX_MSG_SIZE;
        }

        // 1. 获取 ConsumerQueue
        ConsumerQueue cq = consumerQueueManager.get(topic, queueId);
        if (cq == null) {
            log.debug("ConsumerQueue not found: topic={}, queueId={}", topic, queueId);
            return PullResult.noMsgInQueue();
        }

        // 2. 获取队列边界
        long minOffset = cq.getMinLogicOffset();
        long maxOffset = cq.getMaxLogicOffset();

        // 3. 检查偏移量合法性
        if (offset < minOffset || offset > maxOffset + 1) {
            log.warn("Offset illegal: topic={}, queueId={}, offset={}, minOffset={}, maxOffset={}",
                    topic, queueId, offset, minOffset, maxOffset);
            return PullResult.offsetIllegal(minOffset, maxOffset);
        }

        // 4. 遍历索引读取消息
        List<Message> messages = new ArrayList<>();
        int totalSize = 0;
        long nextOffset = offset;

        for (long i = offset; i <= maxOffset && messages.size() < maxMsgCount; i++) {
            try {
                // 获取索引
                CQIndex index = cq.getIndex(i);

                // 检查累计大小
                if (totalSize + index.getSize() > maxMsgSize) {
                    log.debug("Reach max message size: topic={}, queueId={}, offset={}, totalSize={}",
                            topic, queueId, i, totalSize);
                    break;
                }

                // 从 CommitLog 读取消息
                Message msg = readMessageFromCommitLog(topic, index);
                if (msg != null) {
                    messages.add(msg);
                    totalSize += index.getSize();
                    nextOffset = i + 1;
                } else {
                    log.warn("Read message failed: topic={}, queueId={}, offset={}, physicalOffset={}",
                            topic, queueId, i, index.getPhysicalOffset());
                }
            } catch (IndexOutOfBoundsException e) {
                log.warn("Index out of bounds: topic={}, queueId={}, offset={}, maxOffset={}",
                        topic, queueId, i, maxOffset);
                break;
            } catch (Exception e) {
                log.error("Read message error: topic={}, queueId={}, offset={}", topic, queueId, i, e);
                break;
            }
        }

        // 5. 判断是否有新消息
        if (messages.isEmpty()) {
            if (offset >= maxOffset) {
                return PullResult.noNewMsg(maxOffset + 1, minOffset, maxOffset);
            }
        }

        log.debug("Pull result: topic={}, queueId={}, offset={}, found={}, nextOffset={}",
                topic, queueId, offset, messages.size(), nextOffset);

        return PullResult.found(nextOffset, minOffset, maxOffset, messages);
    }

    /**
     * 拉取消息（使用默认参数）
     */
    public PullResult pull(String topic, int queueId, long offset) {
        return pull(topic, queueId, offset, DEFAULT_MAX_MSG_COUNT, DEFAULT_MAX_MSG_SIZE);
    }

    /**
     * 从 CommitLog 读取消息
     *
     * @param topic 主题
     * @param index 索引
     * @return 消息
     */
    private Message readMessageFromCommitLog(String topic, CQIndex index) {
        CommitLog commitLog = commitLogManager.get(topic);
        if (commitLog == null) {
            log.error("CommitLog not found: topic={}", topic);
            return null;
        }

        try {
            byte[] data = commitLog.readContent(
                    (int) index.getPhysicalOffset(),
                    index.getSize()
            );

            if (data == null || data.length == 0) {
                return null;
            }

            // 反序列化消息
            return Message.parseFrom(data);
        } catch (Exception e) {
            log.error("Read from CommitLog error: topic={}, physicalOffset={}, size={}",
                    topic, index.getPhysicalOffset(), index.getSize(), e);
            return null;
        }
    }

    /**
     * 获取队列信息
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return 队列信息 [minOffset, maxOffset, currentIndex]
     */
    public long[] getQueueInfo(String topic, int queueId) {
        ConsumerQueue cq = consumerQueueManager.get(topic, queueId);
        if (cq == null) {
            return new long[]{0, -1, 0};
        }
        return new long[]{cq.getMinLogicOffset(), cq.getMaxLogicOffset(), cq.getIndexCount()};
    }
}
