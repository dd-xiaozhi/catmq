package com.aoaojiao.catmq.broker.loader;

import com.alibaba.fastjson2.JSON;
import com.aoaojiao.catmq.broker.utils.FileContentUtil;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.cache.CommonThreadPool;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.AllArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * 主题配置文件加载器
 *
 * @author DD
 */
@AllArgsConstructor
public class TopicConfigLoader {

    private MessageStoreConfig messageStoreConfig;

    public void loadTopicInfo() throws IOException {

        try {
            String jsonStr = FileContentUtil.readFileTpString(getCatmqTopicFilePath());
            CommonCache.CATMQ_TOPIC_MODEL_CACHE.addAll(
                    JSON.parseArray(jsonStr, CatmqTopicModel.class));
        } catch (Exception e) {
            throw new RuntimeException("load catmq topic info error");
        }
    }

    /**
     * 定时刷盘 topic 信息线程，每隔多少秒刷新一次
     */
    public void flushTopicInfo() {
        CommonThreadPool.refreshCatmqTopicInfoExecutor.execute(() -> {
            // TODO 3 秒刷新一次，后期可扩展成 config 配置
            // 刚启动先让它 park
            LockSupport.parkUntil(3000);
            System.out.println("refresh catmq topic info");
            List<CatmqTopicModel> catmqTopicModelCache = CommonCache.CATMQ_TOPIC_MODEL_CACHE;
            // 重新写回到 topicInfo 文件中
            String jsonStr = JSON.toJSONString(catmqTopicModelCache);
            try {
                FileContentUtil.writeStringToFile(jsonStr, getCatmqTopicFilePath());
            } catch (IOException e) {
                throw new RuntimeException("refresh catmq topic info error");
            }
        });
    }

    private String getCatmqTopicFilePath() {
        String storePathRootDir = messageStoreConfig.getStorePathRootDir();
        /**
         * 存储主题信息文件在 store 目录下的 catmq-topic.json 文件中
         * 可以做成固定的也可以是动态配置的，这里我们选择固定
         */
        return storePathRootDir + File.separator + messageStoreConfig.getTopicInfoFileName();
    }
}
