package com.cloudhopper.smpp;

import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.client.NioWorkerPoolExt;
import com.cloudhopper.smpp.async.client.SessionContextFactory;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public interface AsyncSmppClient {

    /**
     *
     * @return component used to register listeners
     */
    EventDispatcher getEventDispatcher();

    int getConnectionSize();

    NioWorkerPoolExt getWorkerPool();

    void destroy();

    void bind(SmppSessionConfiguration config, BindCallback bindCallback);

    void bind(SmppSessionConfiguration config, BindCallback bindCallback, SessionContextFactory sessionContextFactory);
}
