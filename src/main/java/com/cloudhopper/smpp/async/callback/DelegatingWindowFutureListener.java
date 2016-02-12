package com.cloudhopper.smpp.async.callback;

import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.commons.util.windowing.WindowFutureListener;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.pdu.PduResponse;

/**
 * Created by ib-dtopler on 03.02.16..
 */

public class DelegatingWindowFutureListener<K, R, P extends PduResponse> implements WindowFutureListener<K, R, P>{
    private final PduSentCallback<P> callback;

    public DelegatingWindowFutureListener(PduSentCallback<P> callback) {
        this.callback = callback;
    }

    @Override
    public void onComplete(WindowFuture<K, R, P> windowFuture) {
        callback.onSuccess(windowFuture.getResponse());
    }

    @Override
    public void onFailure(WindowFuture<K, R, P> windowFuture, Throwable throwable) {
        callback.onFailure(throwable);
    }

    @Override
    public void onExpire(WindowFuture<K, R, P> windowFuture) {
        callback.onExpire();
    }

    @Override
    public void onCancel(WindowFuture<K, R, P> windowFuture) {
        callback.onCancel();
    }
}
