package com.cloudhopper.smpp.async.events.support;

import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.AsyncSmppSession;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface EventHandler<R extends SessionEvent> {
    boolean canHandle(R sessionEvent);

    void handle(R sessionEvent, AsyncSmppSession session);
}
