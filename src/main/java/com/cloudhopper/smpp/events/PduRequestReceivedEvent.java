package com.cloudhopper.smpp.events;

import com.cloudhopper.smpp.pdu.PduRequest;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class PduRequestReceivedEvent implements SessionEvent {
    private final PduRequest pduRequest;

    public PduRequestReceivedEvent(PduRequest pduRequest) {
        this.pduRequest = pduRequest;
    }

    public PduRequest getPduRequest() {
        return pduRequest;
    }
}
