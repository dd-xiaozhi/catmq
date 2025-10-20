package com.aoaojiao.catmq.common.model;

import lombok.Data;

import java.util.List;

/**
 * mq的topic映射对象
 * 会持久化到磁盘中，broker 启动时从 catmq-topic.json 文件中加载主题信息
 *
 * @author DD
 */
@Data
public class CatmqTopicModel {

    /**
     * 主题
     */
    private String topic;

    /**
     * 率属于哪个 broker 的
     * TODO 后面分布式的时候会用到
     */
    // private String broker;

    /**
     * 主题对应的 commitLog 文件信息
     */
    private CommitLogModel commitLogModel;

    /**
     * 绑定主题的Queue列表
     */
    private List<QueueModel> QueueModelList;

    /**
     * 创建时间
     */
    private Long createAt;

    /**
     * 更新时间
     */
    private Long updateAt;

}
