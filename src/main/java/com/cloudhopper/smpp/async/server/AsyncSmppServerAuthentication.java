package com.cloudhopper.smpp.async.server;

import com.cloudhopper.smpp.async.events.PduRequestReceivedEvent;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;

/**
 * Created by ib-dtopler on 6/15/16.
 */
public interface AsyncSmppServerAuthentication {
    boolean isValidConnection(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session);
}
