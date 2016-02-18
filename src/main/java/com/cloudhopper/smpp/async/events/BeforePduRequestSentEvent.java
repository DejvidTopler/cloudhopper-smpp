package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class BeforePduRequestSentEvent<R extends PduRequest<P>, P extends PduResponse> extends CtxAwareEvent {
    private volatile boolean stopExecution;

    public BeforePduRequestSentEvent(AsyncRequestContext<R, P> ctx) {
        super(ctx);
    }

    public boolean isStopExecution() {
        return stopExecution;
    }

    public void setStopExecution(boolean stopExecution) {
        this.stopExecution = stopExecution;
    }
}
