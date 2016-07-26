package com.cloudhopper.smpp.async.client;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;

/**
 * Created by ib-dtopler on 6/16/16.
 */
public class NioWorkerPoolExt extends NioWorkerPool {
    public static final Logger LOGGER = LoggerFactory.getLogger(NioWorkerPoolExt.class);

    private final NioWorker[] initializedWorkers;

    public NioWorkerPoolExt(Executor workerExecutor, int workerCount, ThreadNameDeterminer determiner) {
        super(workerExecutor, workerCount, determiner);

        initializedWorkers = new NioWorker[workerCount];
        try {
            Field field = getField(getClass(), "workers");
            field.setAccessible(true);
            Object[] nioWorkers = (Object[]) field.get(this);
            for (int i = 0; i < nioWorkers.length; i++) {
                initializedWorkers[i] = (NioWorker) nioWorkers[i];
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
}
