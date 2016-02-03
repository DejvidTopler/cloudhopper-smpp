package com.cloudhopper.smpp.impl;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.pdu.BaseBindResp;

/**
 * Created by ib-dtopler on 22.01.16..
 */
public interface BindCallback {

    enum Reason {
        /**
         * Connection is canceled before is established
         */
        CONNECT_CANCELED,
        /**
         * Timeout while waiting for connection to be established
         */
        CONNECT_TIMEOUT,
        /**
         * Connect failed due connection refused
         */
        CONNECTION_REFUSED,
        /**
         *
         */
        UNKNOWN, SEND_BIND_REQ_FAILED, NEGATIVE_BIND_RESP, READ_TIMEOUT, READ_ERROR, SSL_FAILURE, INVALID_BIND_TYPE,

    }

    void onBindSucess(SmppSession smppSession);

    void onFailure(Reason reason, Throwable t, BaseBindResp response);

}
