package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.store.model.MessageModel;
import com.aoaojiao.catmq.store.util.MMapUtil;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.Map;

/**
 * commitLog 文件模型
 *
 * @author DD
 */
public class CommitLogFileModel {

    private String topicName;
    private String storePath;
    private MappedByteBuffer mappedByteBuffer;

    /**
     * 加载指定主题名的 commitLog 文件
     *
     * @param topicName
     */
    public void loadingFileInMMap(String topicName,
                                  String storePath,
                                  int startOffset,
                                  int maxOffset) throws IOException {
        this.topicName = topicName;
        this.storePath = storePath;
        this.mappedByteBuffer = MMapUtil.createMappedByteBuffer(getFilePath(), startOffset, maxOffset);
    }

    /**
     * 获取最新 commitLog 文件路径
     * @return 文件路径
     */
    private String getFilePath() {
        Map<String, CatmqTopicModel> catmqTopicModelMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel catmqTopicModel = catmqTopicModelMap.get(this.topicName);
        if (catmqTopicModel == null) {
            throw new IllegalArgumentException(String.format("topic is valid, topicName: [ %s ]", this.topicName));
        }
        CommitLogModel commitLogModel = catmqTopicModel.getCommitLogModel();
        String filename = commitLogModel.getFilename();
        // 文件路径：store目录 + topicName + 当前写入的文件名
        return storePath + File.separator + this.topicName + File.separator + filename;
    }


    public void writeContent(MessageModel messageModel) {
        // TODO 目前先只写入消息体
        MMapUtil.writeContent(this.mappedByteBuffer, messageModel.getContent());
    }

    public byte[] readContent(int offset, int offsetSize) {

        return MMapUtil.readContent(this.mappedByteBuffer, offset, offsetSize);
    }

    public String getTopicName() {
        return this.topicName;
    }
}
