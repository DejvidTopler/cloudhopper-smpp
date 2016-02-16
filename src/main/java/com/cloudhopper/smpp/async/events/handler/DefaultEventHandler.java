package com.cloudhopper.smpp.async.events.handler;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.events.SessionEvent;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class DefaultEventHandler<R extends SessionEvent> implements EventHandler<R> {

    @Override
    public boolean canHandle(R sessionEvent, AsyncSmppSession session) {
        return true;
    }

    @Override
    public void handle(R sessionEvent, AsyncSmppSession session) {
    }

}
