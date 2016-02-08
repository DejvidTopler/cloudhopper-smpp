package com.cloudhopper.smpp.events;

import com.cloudhopper.smpp.impl.AsyncSmppSession;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface EventHandler {
    boolean canHandle(SessionEvent sessionEvent);

    void handle(SessionEvent sessionEvent, AsyncSmppSession session);
}
