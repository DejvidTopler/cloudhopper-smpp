package com.cloudhopper.smpp.events;

import com.cloudhopper.smpp.impl.AsyncSmppSession;

import java.util.List;
import java.util.concurrent.*;

/**
 * Created by ib-dtopler on 30.11.15..
 */
public class EventProcessor {
    private static final int QUEUE_LIMIT = 100_000;

    private final BlockingQueue<Runnable> queue;
    private final Executor executor;
    private final ConcurrentMap<Class<? extends SessionEvent>, List<EventHandler>> handlers;

    public EventProcessor(int threadCount) {

        handlers = new ConcurrentHashMap<>();

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
        List<EventHandler> eventHandlers = handlers.get(sessionEvent.getClass());
        if (eventHandlers == null) {
            return;
        }

        for (EventHandler eventHandler : eventHandlers) {
            try {
                if (eventHandler.canHandle(sessionEvent))
                    eventHandler.handle(sessionEvent, session);
            } catch (Throwable e) {
                //TODO(DT) log
            }
        }
    }

    public boolean hasHandlers(Class<? extends SessionEvent> key){
        return handlers.containsKey(key);
    }

    public void addHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler) {
        handlers.computeIfAbsent(sessionEvent, aClass -> new CopyOnWriteArrayList<>()).add(eventHandler);
    }

    public int getQueueSize() {
        return queue == null ? 0 : queue.size();
    }
}
