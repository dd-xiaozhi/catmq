package com.aoaojiao.catmq.store.model;

import lombok.Data;

/**
 * @author DD
 */
@Data
public class QueueModel {

    private Integer id;
    private Long currentOffset;
    private Long minOffset;
    private Long maxOffset;
}
