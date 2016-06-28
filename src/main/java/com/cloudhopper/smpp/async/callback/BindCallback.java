package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppSession;
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
        UNKNOWN, SEND_BIND_REQ_FAILED, NEGATIVE_BIND_RESP, READ_TIMEOUT, READ_ERROR, SSL_FAILURE, INVALID_BIND_TYPE, INVALID_SESSION_STATE,

    }

    /**
     * called after connection success, before bind req sent. Usually used to register session
     * so window expirer task can expire bindReq
     */
    void onSessionCreate(AsyncSmppSession smppSession);

    void onBindSucess(DefaultAsyncSmppSession smppSession);

    void onFailure(Reason reason, Throwable t, BaseBindResp response);

}
