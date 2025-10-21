package com.aoaojiao.catmq.store.lock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author DD
 */
public class PutMessageReentrantLock implements PutMessageLock {

    private final ReentrantLock reentrantLock = new ReentrantLock();    // 默认非公平锁

    @Override
    public void lock() {
        reentrantLock.lock();
    }

    @Override
    public void unlock() {
        reentrantLock.unlock();
    }
}