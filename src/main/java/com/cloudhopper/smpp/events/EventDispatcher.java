package com.cloudhopper.smpp.events;

import com.cloudhopper.smpp.impl.AsyncSmppSession;

/**
 * Created by ib-dtopler on 23.11.15..
 */
public interface EventDispatcher {

    <E extends SessionEvent> E dispatch(E sessionEvent, AsyncSmppSession session);

    boolean hasHandlers(Class<? extends SessionEvent> key);

    int getQueueSize();

    void addHandler(Class<? extends SessionEvent> sessionEvent, AsyncSmppSession session, EventHandler eventHandler);

    void addSyncHandler(Class<? extends SessionEvent> sessionEvent, AsyncSmppSession session,
            EventHandler eventHandler);
}
