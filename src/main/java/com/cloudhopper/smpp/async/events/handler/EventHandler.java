package com.cloudhopper.smpp.async.events.handler;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.events.SessionEvent;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface EventHandler<R extends SessionEvent> {
    boolean canHandle(R sessionEvent);

    void handle(R sessionEvent, AsyncSmppSession session);
}
