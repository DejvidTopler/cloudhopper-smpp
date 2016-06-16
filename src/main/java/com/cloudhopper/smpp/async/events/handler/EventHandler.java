package com.cloudhopper.smpp.async.events.handler;

import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface EventHandler<R extends SessionEvent> {
    boolean canHandle(R sessionEvent, AsyncSmppSession session);

    void handle(R sessionEvent, AsyncSmppSession session);
}
