package com.aoaojiao.catmq.store.lock;

/**
 * @author DD
 */
public interface PutMessageLock {

    void lock();

    void unlock();
}
