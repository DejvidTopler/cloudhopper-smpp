package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class UnexpectedPduResponseReceivedEvent implements SessionEvent {

    private final PduResponse pduResponse;

    public UnexpectedPduResponseReceivedEvent(PduResponse pduResponse) {
        this.pduResponse = pduResponse;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }
}