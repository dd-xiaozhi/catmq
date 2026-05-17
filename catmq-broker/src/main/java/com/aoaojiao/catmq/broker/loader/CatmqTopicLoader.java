package com.aoaojiao.catmq.broker.loader;

import com.alibaba.fastjson2.JSON;
import com.aoaojiao.catmq.broker.utils.FileContentUtil;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 主题配置文件加载器
 *
 * @author DD
 */
@Slf4j
public class CatmqTopicLoader extends BaseLoader<List<CatmqTopicModel>> {

    public CatmqTopicLoader(MessageStoreConfig messageStoreConfig) {
        super(messageStoreConfig);
    }

    @Override
    protected List<CatmqTopicModel> loadData() {
        try {
            String jsonStr = FileContentUtil.readFileTpString(getFilePath());
            return JSON.parseArray(jsonStr, CatmqTopicModel.class);
        } catch (Exception e) {
            throw new RuntimeException("load catmq topic info error", e);
        }
    }

    @Override
    protected List<CatmqTopicModel> getData() {
        return CommonCache.getCatmqTopicModelList();
    }

    @Override
    protected void setData(List<CatmqTopicModel> data) {
        CommonCache.setCatmqTopicModelCache(data);
    }

    @Override
    protected String getFilePath() {
        return messageStoreConfig.getTopicInfoFilePath();
    }

    @Override
    protected long getFlushIntervalMs() {
        return messageStoreConfig.getTopicInfoFlushIntervalMs();
    }
}