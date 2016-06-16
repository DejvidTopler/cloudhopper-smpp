package com.cloudhopper.smpp.async.events.handler;

import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public interface DefaultEventHandler<R extends SessionEvent> extends EventHandler<R> {

    @Override
    default boolean canHandle(R sessionEvent, AsyncSmppSession session) {
        return true;
    }

}
