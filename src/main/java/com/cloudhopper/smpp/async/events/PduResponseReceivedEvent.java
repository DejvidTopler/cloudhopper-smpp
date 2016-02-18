package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class PduResponseReceivedEvent<R extends PduRequest<P>, P extends PduResponse> extends CtxAwareEvent<R, P> {

    private final PduResponse pduResponse;

    public PduResponseReceivedEvent(AsyncRequestContext<R, P> ctx, PduResponse pduResponse) {
        super(ctx);
        this.pduResponse = pduResponse;
    }

    public PduResponse getPduResponse() {
        return pduResponse;
    }

}