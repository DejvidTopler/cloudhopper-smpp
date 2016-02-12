package com.cloudhopper.smpp.async.events.support;

import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.events.handler.EventHandler;


/**
 * Created by ib-dtopler on 23.11.15..
 */
public class EventDispatcherImpl implements EventDispatcher {

    private final EventProcessor asyncProcessor = new EventProcessor(1);
    private final EventProcessor syncProcessor = new EventProcessor(0);

    @Override
    public <E extends SessionEvent> E dispatch(E sessionEvent, AsyncSmppSession session) {
        asyncProcessor.dispatch(sessionEvent, session);
        syncProcessor.dispatch(sessionEvent, session);
        return sessionEvent;
    }

    @Override
    public boolean hasHandlers(Class<? extends SessionEvent> key) {
        return syncProcessor.hasHandlers(key) || asyncProcessor.hasHandlers(key);
    }

    @Override
    public int getQueueSize() {
        return asyncProcessor.getQueueSize();
    }

    @Override
    public void addHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler) {
        syncProcessor.addHandler(sessionEvent, eventHandler);
    }

    @Override
    public void addAsyncHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler) {
        asyncProcessor.addHandler(sessionEvent, eventHandler);
    }
}
