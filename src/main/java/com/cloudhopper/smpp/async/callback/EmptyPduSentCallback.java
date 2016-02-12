package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class EmptyPduSentCallback implements PduSentCallback {

    @Override
    public void onSuccess(PduResponse response) {
    }

    @Override
    public void onFailure(Throwable t) {
    }

    @Override
    public void onExpire() {
    }

    @Override
    public void onCancel() {
    }
}
