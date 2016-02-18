package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 18.02.16..
 */
public class CtxAwareEvent<R extends PduRequest<P>, P extends PduResponse> implements SessionEvent{

    private final AsyncRequestContext<R, P> ctx;

    public CtxAwareEvent(AsyncRequestContext<R, P> ctx) {
        this.ctx = ctx;
    }

    public AsyncRequestContext<R, P> getCtx() {
        return ctx;
    }
}
