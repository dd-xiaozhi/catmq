package com.aoaojiao.catmq.broker.loader;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.aoaojiao.catmq.broker.utils.FileContentUtil;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.cache.CommonThreadPool;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 主题配置文件加载器
 *
 * @author DD
 */
@AllArgsConstructor
public class CatmqTopicLoader {

    private MessageStoreConfig messageStoreConfig;

    public void loadTopicInfo() {

        try {
            String jsonStr = FileContentUtil.readFileTpString(messageStoreConfig.getTopicInfoFilePath());
            CommonCache.setCatmqTopicModelCache(JSON.parseArray(jsonStr, CatmqTopicModel.class));
        } catch (Exception e) {
            throw new RuntimeException("load catmq topic info error");
        }
    }

    /**
     * 定时刷盘 topic 信息线程，每隔多少秒刷新一次
     */
    public void startFlushTopicInfoThread() {
        CommonThreadPool.refreshCatmqTopicFileExecutor.execute(() -> {
            while (true) {
                // TODO 3 秒刷新一次，后期可扩展成 config 配置
                // 刚启动先让它 park
                LockSupport.parkNanos(3_000_000_000L);
                System.out.println("refresh catmq topic info");
                List<CatmqTopicModel> catmqTopicModelCache = CommonCache.getCatmqTopicModelList();
                // 重新写回到 topicInfo 文件中
                String jsonStr = JSON.toJSONString(catmqTopicModelCache, JSONWriter.Feature.PrettyFormat);
                try {
                    FileContentUtil.writeStringToFile(messageStoreConfig.getTopicInfoFilePath(), jsonStr);
                } catch (IOException e) {
                    throw new RuntimeException("refresh catmq topic info error", e);
                }
            }
        });
    }
}
