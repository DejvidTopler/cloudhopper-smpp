package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 22.01.16..
 */
public interface PduSentCallback<P extends PduResponse> {

    enum CancelReason {
        INVALID_SESSION_STATE, OTHER, STOPPED_EXECUTION
    }

    void onSuccess(P response);

    void onFailure(Throwable t);

    void onExpire();

    void onCancel(CancelReason cancelReason);
}
