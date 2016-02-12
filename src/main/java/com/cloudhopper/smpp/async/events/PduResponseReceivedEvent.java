package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class PduResponseReceivedEvent implements SessionEvent {

    private final PduResponse pduResponse;
    private final PduRequest pduRequest;

    public PduResponseReceivedEvent(PduRequest pduRequest, PduResponse pduResponse) {
        this.pduRequest = pduRequest;
        this.pduResponse = pduResponse;
    }

    public PduRequest getPduRequest() {
        return pduRequest;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }
}