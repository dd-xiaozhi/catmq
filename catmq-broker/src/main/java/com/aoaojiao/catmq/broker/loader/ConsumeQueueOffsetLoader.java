package com.aoaojiao.catmq.broker.loader;

import com.alibaba.fastjson2.JSON;
import com.aoaojiao.catmq.broker.utils.FileContentUtil;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.ConsumeQueueOffsetModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费队列偏移量加载器
 * 加载 consume-queue-offset.json 文件
 *
 * @author DD
 */
@Slf4j
public class ConsumeQueueOffsetLoader extends BaseLoader<ConsumeQueueOffsetModel> {

    public ConsumeQueueOffsetLoader(MessageStoreConfig messageStoreConfig) {
        super(messageStoreConfig);
    }

    @Override
    protected ConsumeQueueOffsetModel loadData() {
        try {
            String jsonStr = FileContentUtil.readFileTpString(getFilePath());
            return JSON.parseObject(jsonStr, ConsumeQueueOffsetModel.class);
        } catch (Exception e) {
            throw new RuntimeException("load catmq consume queue offset info error", e);
        }
    }

    @Override
    protected ConsumeQueueOffsetModel getData() {
        return CommonCache.getConsumeQueueOffsetModelCache();
    }

    @Override
    protected void setData(ConsumeQueueOffsetModel data) {
        CommonCache.setConsumeQueueOffsetModelCache(data);
    }

    @Override
    protected String getFilePath() {
        return messageStoreConfig.getConsumeQueueOffsetFilePath();
    }

    @Override
    protected long getFlushIntervalMs() {
        return messageStoreConfig.getConsumeQueueOffsetFlushIntervalMs();
    }
}