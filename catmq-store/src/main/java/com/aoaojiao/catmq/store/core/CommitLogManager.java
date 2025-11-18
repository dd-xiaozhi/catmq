package com.aoaojiao.catmq.store.core;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CommitLogFile 管理器
 *
 * @author DD
 */
public class CommitLogManager {

    public static final Map<String, CommitLog> commitLogFileModelMap = new ConcurrentHashMap<>();

    public CommitLog get(String topicName) {
        return commitLogFileModelMap.get(topicName);
    }

    public void put(CommitLog commitLog) {
        if (Objects.isNull(commitLog)) {
            throw new RuntimeException("commitLog is null");
        }

        if (StringUtils.isBlank(commitLog.getTopicName())) {
            throw new IllegalArgumentException("topicName is blank");
        }

        commitLogFileModelMap.put(commitLog.getTopicName(), commitLog);
    }
}
