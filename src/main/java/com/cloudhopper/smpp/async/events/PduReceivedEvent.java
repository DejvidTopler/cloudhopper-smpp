package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.Pdu;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class PduReceivedEvent implements SessionEvent {

    private final Pdu pdu;

    private volatile boolean stopExecution;

    public PduReceivedEvent(Pdu pdu) {
        this.pdu = pdu;
    }

    public Pdu getPdu() {
        return pdu;
    }

    public boolean isStopExecution() {
        return stopExecution;
    }

    public void setStopExecution(boolean stopExecution) {
        this.stopExecution = stopExecution;
    }
}