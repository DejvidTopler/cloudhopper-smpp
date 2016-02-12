package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class BeforePduResponseSentEvent implements SessionEvent {

    private final PduResponse pduResponse;

    private volatile boolean stopExecution;


    public BeforePduResponseSentEvent(PduResponse pduResponse) {
        this.pduResponse = pduResponse;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }

    public boolean isStopExecution() {
        return stopExecution;
    }

    public void setStopExecution(boolean stopExecution) {
        this.stopExecution = stopExecution;
    }
}
