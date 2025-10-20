package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.store.model.MessageModel;
import com.aoaojiao.catmq.store.util.MMapUtil;

import java.io.IOException;
import java.nio.MappedByteBuffer;

/**
 * commitLog 文件模型
 *
 * @author DD
 */
public class CommitLogFileModel {

    private String topicName;
    private MappedByteBuffer mappedByteBuffer;


    /**
     * 加载指定主题名的 commitLog 文件
     *
     * @param topicName
     */
    public void loadingFileInMMap(String topicName,
                                     int startOffset,
                                     int maxOffset) throws IOException {
        this.topicName = topicName;
        String filePath = getLastNewFilePath(topicName);
        this.mappedByteBuffer = MMapUtil.createMappedByteBuffer(filePath, startOffset, maxOffset);
    }

    /**
     * 获取最新 commitLog 文件路径
     *
     * @param topicName 主题名
     * @return 文件路径
     */
    private String getLastNewFilePath(String topicName) {
        CatmqTopicModel catmqTopicModel = CommonCache.CATMQ_TOPIC_MODEL_CACHE.get(topicName);
        if (catmqTopicModel == null) {
            throw new IllegalArgumentException(String.format("topic is valid, topicName: [ %s ]", topicName));
        }
        CommitLogModel commitLogModel = catmqTopicModel.getCommitLogModel();
        String filename = commitLogModel.getFilename();
        

        return null;
    }


    public void writeContent(MessageModel messageModel) {
        // 判断是否已经满了
    }

    public byte[] readContent(int offset, int offsetSize) {

        return MMapUtil.readContent(this.mappedByteBuffer, offset, offsetSize);
    }

    public String getTopicName() {
        return this.topicName;
    }
}
