package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduRequest;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class PduRequestSentEvent implements SessionEvent {
    private final PduRequest pduRequest;

    public PduRequestSentEvent(PduRequest pduRequest) {
        this.pduRequest = pduRequest;
    }

    public PduRequest getPduRequest() {
        return pduRequest;
    }

}
