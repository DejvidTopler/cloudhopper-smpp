package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.smpp.pdu.PduResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class DefaultPduSentCallback implements PduSentCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPduSentCallback.class);

    @Override
    public void onSuccess(PduResponse response) {
        LOGGER.info("Successfully received response: " + response);
    }

    @Override
    public void onFailure(Throwable t) {
        LOGGER.info("Failure while sending pdu, message: " + t, t);
    }

    @Override
    public void onExpire() {
        LOGGER.info("Pdu expired");
    }

    @Override
    public void onCancel(CancelReason cancelReason) {
        LOGGER.info("Pdu canceled " + cancelReason, new RuntimeException());
    }
}
