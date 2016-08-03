package com.cloudhopper.smpp.async.client;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Created by ib-dtopler on 6/16/16.
 */
public class NioWorkerPoolExt extends NioWorkerPool {
    public static final Logger LOGGER = LoggerFactory.getLogger(NioWorkerPoolExt.class);

    private final NioWorker[] initializedWorkers;
    private final Queue<Runnable>[] initializedQueues;

    public NioWorkerPoolExt(Executor workerExecutor, int workerCount, ThreadNameDeterminer determiner) {
        super(workerExecutor, workerCount, determiner);

        initializedWorkers = new NioWorker[workerCount];
        initializedQueues = new Queue[workerCount];
        try {
            Field field = getField(getClass(), "workers");
            field.setAccessible(true);
            Object[] nioWorkers = (Object[]) field.get(this);
            for (int i = 0; i < nioWorkers.length; i++) {
                initializedWorkers[i] = (NioWorker) nioWorkers[i];

                Field taskQueueField = getField(initializedWorkers[i].getClass(), "taskQueue");
                taskQueueField.setAccessible(true);
                initializedQueues[i] = (Queue<Runnable>) taskQueueField.get(initializedWorkers[i]);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error creating reference to workers.");
        }
    }

    private Field getField(Class clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException var3) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), fieldName);
            } else {
                throw new IllegalStateException("Could not locate field \'" + fieldName + "\' on class " + clazz);
            }
        }
    }

    @Override
    public NioWorker nextWorker() {
        NioWorker worker = DefaultAsyncSmppClient.CURR_THREAD_WORKER.get();
        return worker == null ? super.nextWorker() : worker;
    }

    public NioWorker getWorkerByGatewayId(int gatewayId) {
        return initializedWorkers[gatewayId % initializedWorkers.length];
    }

    public NioWorker[] getWorkers() {
        return initializedWorkers;
    }

    public int getSize() {
        int size = 0;
        for (Queue<Runnable> q : initializedQueues)
            size += q.size();
        return size;
    }
}
