package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 18.02.16..
 */
public class AsyncRequestContext<R extends PduRequest<P>, P extends PduResponse> {

    private final R request;
    private final AsyncSmppSession session;
    private final PduSentCallback<P> callback;
    private volatile long windowTimeout;
    private volatile long expireTimestamp;
    private volatile long insertTimestamp;


    public AsyncRequestContext(R request, AsyncSmppSession session, PduSentCallback<P> callback) {
        this.request = request;
        this.session = session;
        this.callback = callback;
    }

    public R getRequest() {
        return request;
    }

    public AsyncSmppSession getSession() {
        return session;
    }

    public PduSentCallback<P> getCallback() {
        return callback;
    }

    public long getWindowTimeout() {
        return windowTimeout;
    }

    public void setWindowTimeout(long windowTimeout) {
        this.windowTimeout = windowTimeout;
    }

    public long getExpireTimestamp() {
        return expireTimestamp;
    }

    public void setExpireTimestamp(long expireTimestamp) {
        this.expireTimestamp = expireTimestamp;
    }

    public long getInsertTimestamp() {
        return insertTimestamp;
    }

    public void setInsertTimestamp(long insertTimestamp) {
        this.insertTimestamp = insertTimestamp;
    }
}
