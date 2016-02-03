package com.cloudhopper.smpp.impl;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 22.01.16..
 */
public interface PduSentCallback<P extends PduResponse> {

    void onSuccess(P response);

    void onFailure(Throwable t);

    void onExpire();

    void onCancel();
}
