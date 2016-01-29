package com.cloudhopper.commons.util.windowing;

import java.util.EventListener;

/**
 * Created by ib-dtopler on 22.01.16..
 */
public interface WindowFutureListener<K, R, P> extends EventListener {
    void onComplete(WindowFuture<K, R, P> windowFuture);

    void onFailure(WindowFuture windowFuture, Throwable e);

    void onExpire(DefaultWindowFuture<K, R, P> windowFuture);
}
