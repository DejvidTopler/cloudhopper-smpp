package com.cloudhopper.smpp.async.events.support;

import com.cloudhopper.smpp.async.ConcurrentTable;
import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.async.events.handler.EventHandler;
import com.cloudhopper.smpp.async.events.support.EventDispatcher.ExecutionOrder;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * Created by ib-dtopler on 30.11.15..
 */
public class EventProcessor {
    public static final Logger LOGGER = LoggerFactory.getLogger(EventProcessor.class);
    private static final int QUEUE_LIMIT = 100_000;

    private final BlockingQueue<Runnable> queue;
    private final Executor executor;
    private final ConcurrentTable<Class<? extends SessionEvent>, ExecutionOrder, List<EventHandler>> handlers;

    public EventProcessor(int threadCount) {

        handlers = new ConcurrentTable<>(10, 10);

        if (threadCount > 0) {
            queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
            executor = new ThreadPoolExecutor(threadCount, threadCount, 1L, TimeUnit.HOURS, queue, r -> {
                return new Thread(r, "SmppEventProcessorThread");
            });
        } else {
            queue = null;
            executor = null;
        }
    }

    public void dispatch(SessionEvent sessionEvent, AsyncSmppSession session) {
        if (executor == null)
            execute(sessionEvent, session);
        else
            executor.execute(() -> execute(sessionEvent, session));
    }

    private void execute(SessionEvent sessionEvent, AsyncSmppSession session) {
        for (ExecutionOrder order : ExecutionOrder.values()) {
            List<EventHandler> eventHandlers = handlers.get(sessionEvent.getClass(), order);
            if (eventHandlers == null) {
                continue;
            }

            for (EventHandler eventHandler : eventHandlers) {
                try {
                    if (eventHandler.canHandle(sessionEvent, session))
                        eventHandler.handle(sessionEvent, session);
                } catch (Throwable e) {
                    LOGGER.error("Executing handler failed, handler=" + eventHandler + ", message=" + e.getMessage(), e);
                }
            }
        }
    }

    public boolean hasHandlers(Class<? extends SessionEvent> key) {
        return handlers.row(key).size() > 0;
    }

    public void addHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler,
            ExecutionOrder executionOrder) {
        handlers.get(sessionEvent, executionOrder, aClass -> new CopyOnWriteArrayList<>()).add(eventHandler);
    }

    public int getQueueSize() {
        return queue == null ? 0 : queue.size();
    }
}
