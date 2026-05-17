package com.aoaojiao.catmq.store.config;

import com.aoaojiao.catmq.store.constants.StoreConstant;
import com.aoaojiao.catmq.store.util.ConfigUtil;
import lombok.Data;

import java.io.File;

/**
 * @author DD
 * <p>
 * 消息存储配置
 * <p>
 * 支持多数据来源配置（优先级从高到低）：
 * 1. 系统属性（-Dcatmq.store.xxx）
 * 2. 环境变量（CATMQ_STORE_XXX）
 * 3. 代码默认值
 */
@Data
public class MessageStoreConfig {

    /**
     * 存储根目录
     * - 系统属性：-Dcatmq.store.path=/path
     * - 环境变量：CATMQ_STORE_PATH
     * - 默认值：D:\Work\project\catmq\catmq\store
     */
    private String storePathRootDir = ConfigUtil.getString("path",
            "D:\\Work\\project\\catmq\\catmq\\store");

    /**
     * CommitLog 目录路径（由 storePathRootDir 派生）
     */
    private String commitLogDirPath;

    /**
     * ConsumeQueue 目录路径（由 storePathRootDir 派生）
     */
    private String consumeQueueDirPath;

    /**
     * Topic 信息文件路径（由 storePathRootDir 派生）
     */
    private String topicInfoFilePath;

    /**
     * ConsumeQueue 消费偏移量文件路径（由 storePathRootDir 派生）
     */
    private String consumeQueueOffsetFilePath;

    /**
     * 消息 CommitLog 文件大小
     * - 系统属性：-Dcatmq.store.message.commitlog.file.size=1073741824
     * - 环境变量：CATMQ_STORE_MESSAGE_COMMITLOG_FILE_SIZE
     * - 默认值：1GB
     */
    private Integer messageCommitLogFileSize = ConfigUtil.getInt("message.commitlog.file.size",
            StoreConstant.DEFAULT_MESSAGE_COMMIT_LOG_FILE_SIZE);

    /**
     * ConsumeQueue CommitLog 文件大小
     * - 系统属性：-Dcatmq.store.consumequeue.commitlog.file.size=12582912
     * - 环境变量：CATMQ_STORE_CONSUMEQUEUE_COMMITLOG_FILE_SIZE
     * - 默认值：12MB
     */
    private Integer consumeQueueCommitLogFileSize = ConfigUtil.getInt("consumequeue.commitlog.file.size",
            StoreConstant.DEFAULT_CONSUME_QUEUE_COMMIT_LOG_FILE_SIZE);

    /**
     * 主题信息刷新间隔（毫秒），默认 3 秒
     */
    private Long topicInfoFlushIntervalMs = 3000L;

    /**
     * 消费队列偏移量刷新间隔（毫秒），默认 3 秒
     */
    private Long consumeQueueOffsetFlushIntervalMs = 3000L;

    /**
     * 构造函数，初始化派生路径
     */
    public MessageStoreConfig() {
        initDerivedPaths();
    }

    /**
     * 初始化派生路径
     * 将 storePathRootDir 转换为绝对路径，并设置各子目录和文件路径
     */
    private void initDerivedPaths() {
        // 转换为绝对路径
        File rootDir = new File(storePathRootDir);
        String absolutePath = rootDir.getAbsolutePath();

        this.commitLogDirPath = absolutePath + File.separator + "commitLog";
        this.consumeQueueDirPath = absolutePath + File.separator + "consumeQueue";
        this.topicInfoFilePath = absolutePath + File.separator + "catmq-topic.json";
        this.consumeQueueOffsetFilePath = absolutePath + File.separator + "consume-queue-offset.json";
    }

    /**
     * 设置存储根目录并更新派生路径
     *
     * @param storePathRootDir 存储根目录
     */
    public void setStorePathRootDir(String storePathRootDir) {
        this.storePathRootDir = storePathRootDir;
        initDerivedPaths();
    }
}