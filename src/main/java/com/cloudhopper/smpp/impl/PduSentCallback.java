package com.cloudhopper.smpp.impl;

import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 22.01.16..
 */
public interface PduSentCallback<R extends PduRequest, P extends PduResponse> {

    void onSuccess(R request, P response);

    void onFailure(Throwable t);

}
