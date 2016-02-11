package com.cloudhopper.smpp.events;

import com.cloudhopper.smpp.impl.AsyncSmppSession;


/**
 * Created by ib-dtopler on 23.11.15..
 */
public class EventDispatcherImpl implements EventDispatcher {

    private final EventProcessor asyncProcessor = new EventProcessor(1);
    private final EventProcessor syncProcessor = new EventProcessor(0);

    @Override
    public void dispatch(SessionEvent sessionEvent, AsyncSmppSession session) {
        asyncProcessor.dispatch(sessionEvent, session);
        syncProcessor.dispatch(sessionEvent, session);
    }

    @Override
    public boolean hasHandlers(Class<? extends SessionEvent> key){
        return syncProcessor.hasHandlers(key) || asyncProcessor.hasHandlers(key);
    }

    @Override
    public int getQueueSize() {
        return asyncProcessor.getQueueSize();
    }

    @Override
    public void addHandler(Class<? extends SessionEvent> sessionEvent, AsyncSmppSession session,
            EventHandler eventHandler) {
        asyncProcessor.addHandler(sessionEvent, eventHandler);
    }

    @Override
    public void addSyncHandler(Class<? extends SessionEvent> sessionEvent, AsyncSmppSession session,
            EventHandler eventHandler) {
        syncProcessor.addHandler(sessionEvent, eventHandler);
    }
}
