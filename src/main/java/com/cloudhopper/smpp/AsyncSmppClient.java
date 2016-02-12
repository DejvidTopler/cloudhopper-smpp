package com.cloudhopper.smpp;

import com.cloudhopper.smpp.async.callback.BindCallback;
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

    void destroy();

    void bindAsync(SmppSessionConfiguration config);

    void bindAsync(SmppSessionConfiguration config, BindCallback bindCallback);
}
