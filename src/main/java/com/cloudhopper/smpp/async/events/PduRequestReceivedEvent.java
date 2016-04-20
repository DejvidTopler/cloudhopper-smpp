package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class PduRequestReceivedEvent implements SessionEvent {
    private final PduRequest pduRequest;
    private final PduResponse pduResponse;
    private volatile boolean stopExecution;

    public PduRequestReceivedEvent(PduRequest pduRequest, PduResponse pduResponse) {
        this.pduRequest = pduRequest;
        this.pduResponse = pduResponse;
    }

    public PduRequest getPduRequest() {
        return pduRequest;
    }

    public boolean isStopExecution() {
        return stopExecution;
    }

    public void setStopExecution(boolean stopExecution) {
        this.stopExecution = stopExecution;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }
}
