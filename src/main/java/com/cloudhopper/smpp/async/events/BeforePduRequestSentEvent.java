package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduRequest;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class BeforePduRequestSentEvent implements SessionEvent {
    private final PduRequest pduRequest;

    private volatile boolean stopExecution;

    public BeforePduRequestSentEvent(PduRequest pduRequest) {
        this.pduRequest = pduRequest;
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
}
