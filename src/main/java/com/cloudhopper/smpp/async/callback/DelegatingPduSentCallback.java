package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public class DelegatingPduSentCallback implements PduSentCallback {

    private final PduSentCallback delegate;

    public DelegatingPduSentCallback(PduSentCallback delegate) {
        this.delegate = delegate;
    }


    @Override
    public void onSuccess(PduResponse response) {
        delegate.onSuccess(response);
    }

    @Override
    public void onFailure(Throwable t, String message) {
        delegate.onFailure(t, null);
    }

    @Override
    public void onExpire() {
        delegate.onExpire();
    }

    @Override
    public void onCancel(CancelReason cancelReason) {
        delegate.onCancel(cancelReason);
    }
}
