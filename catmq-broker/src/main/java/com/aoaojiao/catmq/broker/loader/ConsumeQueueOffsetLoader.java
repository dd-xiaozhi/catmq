package com.aoaojiao.catmq.broker.loader;

import com.alibaba.fastjson2.JSON;
import com.aoaojiao.catmq.broker.utils.FileContentUtil;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.cache.CommonThreadPool;
import com.aoaojiao.catmq.common.model.ConsumeQueueOffsetModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * 加载 consume-queue-offset.json
 * TODO 后期需要抽下一个类专门加载这些记录信息的文件
 *
 * @author DD
 */
@AllArgsConstructor
public class ConsumeQueueOffsetLoader {

    private MessageStoreConfig messageStoreConfig;

    public void loadConsumeQueueOffsetInfo() {
        try {
            String jsonStr = FileContentUtil.readFileTpString(messageStoreConfig.getConsumeQueueOffsetFilePath());
            CommonCache.setConsumeQueueOffsetModelCache(JSON.parseObject(jsonStr, ConsumeQueueOffsetModel.class));
        } catch (Exception e) {
            throw new RuntimeException("load catmq consume queue offset info error", e);
        }
    }

    /**
     * 定时刷盘 topic 信息线程，每隔多少秒刷新一次
     */
    public void startConsumeQueueOffsetInfoThread() {
        CommonThreadPool.refreshConsumeQueueOffsetFileExecutor.execute(() -> {
            while (true) {
                // TODO 3 秒刷新一次，后期可扩展成 config 配置
                // 刚启动先让它 park
                LockSupport.parkNanos(3_000_000_000L);
                System.out.println("refresh catmq consume queue offset info");
                ConsumeQueueOffsetModel catmqTopicModelCache = CommonCache.getConsumeQueueOffsetModelCache();
                // 重新写回到 topicInfo 文件中
                String jsonStr = JSON.toJSONString(catmqTopicModelCache);
                try {
                    FileContentUtil.writeStringToFile(messageStoreConfig.getConsumeQueueOffsetFilePath(), jsonStr);
                } catch (IOException e) {
                    throw new RuntimeException("refresh catmq topic info error");
                }
            }
        });
    }
}
