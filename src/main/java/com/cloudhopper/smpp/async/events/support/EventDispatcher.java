package com.cloudhopper.smpp.async.events.support;

import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.async.events.handler.EventHandler;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;

/**
 * Created by ib-dtopler on 23.11.15..
 */
public interface EventDispatcher {

    enum ExecutionOrder {
        BEFORE,
        NORMAL,
        AFTER
    }

    <E extends SessionEvent> E dispatch(E sessionEvent, AsyncSmppSession session);

    boolean hasHandlers(Class<? extends SessionEvent> key);

    int getQueueSize();

    void addHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler);

    void addHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler, ExecutionOrder executionOrder);

    void addAsyncHandler(Class<? extends SessionEvent> sessionEvent, EventHandler eventHandler);
}
