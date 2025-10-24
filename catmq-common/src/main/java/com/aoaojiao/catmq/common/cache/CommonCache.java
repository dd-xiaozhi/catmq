package com.aoaojiao.catmq.common.cache;

import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.ConsumeQueueOffsetModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公共缓存
 * 所有缓存会放在这里进行管理
 *
 * @author DD
 */
public class CommonCache {


    private static List<CatmqTopicModel> CATMQ_TOPIC_MODEL_CACHE = new ArrayList<>();
    private static ConsumeQueueOffsetModel CONSUME_QUEUE_OFFSET_MODEL_CACHE = new ConsumeQueueOffsetModel();


    public static List<CatmqTopicModel> getCatmqTopicModelList() {
        return CATMQ_TOPIC_MODEL_CACHE;
    }

    public static List<CatmqTopicModel> getCatmqTopicModelCache() {
        return CATMQ_TOPIC_MODEL_CACHE;
    }

    public static void setCatmqTopicModelCache(List<CatmqTopicModel> catmqTopicModelCache) {
        CATMQ_TOPIC_MODEL_CACHE = catmqTopicModelCache;
    }

    public static Map<String, CatmqTopicModel> getCatmqTopicModelMap() {
        return CATMQ_TOPIC_MODEL_CACHE.stream()
                .collect(Collectors.toMap(CatmqTopicModel::getTopic, it -> it));
    }

    public static ConsumeQueueOffsetModel getConsumeQueueOffsetModelCache() {
        return CONSUME_QUEUE_OFFSET_MODEL_CACHE;
    }

    public static void setConsumeQueueOffsetModelCache(ConsumeQueueOffsetModel consumeQueueOffsetModelCache) {
        CONSUME_QUEUE_OFFSET_MODEL_CACHE = consumeQueueOffsetModelCache;
    }
}
