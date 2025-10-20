package com.aoaojiao.catmq.store.core;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CommitLogFile 管理器
 *
 * @author DD
 */
public class CommitLogFileModeManager {

    // 并发情况下要使用线程安全的类
    public static final Map<String, CommitLogFileModel> commitLogFileModelMap = new HashMap<>();

    public CommitLogFileModel get(String topicName) {
        return commitLogFileModelMap.get(topicName);
    }

    public void setCommitLogFileModel(CommitLogFileModel commitLogFileModel) {
        if (Objects.isNull(commitLogFileModel)) {
            throw new RuntimeException("commitLogFileModel is null");
        }

        if (StringUtils.isBlank(commitLogFileModel.getTopicName())) {
            throw new IllegalArgumentException("topicName is blank");
        }

        commitLogFileModelMap.put(commitLogFileModel.getTopicName(), commitLogFileModel);
    }
}
