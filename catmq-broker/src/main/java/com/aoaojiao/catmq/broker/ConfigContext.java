package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.Data;

/**
 * @author DD
 */
@Data
public class ConfigContext {

    private MessageStoreConfig messageStoreConfig;
    
}
