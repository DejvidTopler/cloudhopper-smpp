package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class PduRequestSentEvent<R extends PduRequest<P>, P extends PduResponse> extends CtxAwareEvent<R, P> {
    public PduRequestSentEvent(AsyncRequestContext<R, P> ctx) {
        super(ctx);
    }
}
