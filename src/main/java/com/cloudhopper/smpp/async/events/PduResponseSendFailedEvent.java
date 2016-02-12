package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class PduResponseSendFailedEvent implements SessionEvent {

    private final PduResponse pduResponse;
    private final Throwable reason;

    public PduResponseSendFailedEvent(PduResponse pduResponse, Throwable reason) {
        this.pduResponse = pduResponse;
        this.reason = reason;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }

    public Throwable getReason() {
        return reason;
    }
}