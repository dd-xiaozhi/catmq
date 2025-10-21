package com.aoaojiao.catmq.common.cache;

import com.aoaojiao.catmq.common.model.CatmqTopicModel;

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

    
    public final static List<CatmqTopicModel> CATMQ_TOPIC_MODEL_CACHE = new ArrayList<>();
    public static Map<String, CatmqTopicModel> getCatmqTopicModelMap() {
        return CATMQ_TOPIC_MODEL_CACHE.stream()
                .collect(Collectors.toMap(CatmqTopicModel::getTopic, it -> it));
    }
    
    
}
