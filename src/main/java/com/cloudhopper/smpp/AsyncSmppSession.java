package com.cloudhopper.smpp;

import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.async.AsyncWindow;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.impl.SmppSessionChannelListener;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.PduResponse;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface AsyncSmppSession extends SmppSessionChannelListener {


    void bind(BaseBind request, BindCallback bindCallback);

    void unbind(PduSentCallback callback);

    void unbind(PduSentCallback callback, long windowTimeout);

    void sendRequest(AsyncRequestContext ctx);

    void sendResponsePdu(PduResponse pdu);

    void destroy();

    SmppBindType getBindType();

    long getBoundTime();

    String getStateName();

    boolean isOpen();

    boolean isBinding();

    boolean isBound();

    boolean isUnbinding();

    boolean isClosed();

    SmppSessionConfiguration getConfiguration();

    Channel getChannel();

    AsyncWindow getSendWindow();
}
