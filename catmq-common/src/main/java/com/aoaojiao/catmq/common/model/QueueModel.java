package com.aoaojiao.catmq.common.model;

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
