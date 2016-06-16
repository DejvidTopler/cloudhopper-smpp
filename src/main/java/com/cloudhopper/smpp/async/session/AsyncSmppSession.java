package com.cloudhopper.smpp.async.session;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.async.AsyncWindow;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.impl.SmppSessionChannelListener;
import com.cloudhopper.smpp.pdu.PduResponse;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 6/15/16.
 */
public interface AsyncSmppSession extends SmppSessionChannelListener {

    void unbind(PduSentCallback callback);

    void unbind(PduSentCallback callback, long windowTimeout);

    void sendRequest(AsyncRequestContext ctx);

    void sendResponsePdu(PduResponse pdu);

    void destroy();

    SmppBindType getBindType();

    void setBound();

    long getBoundTime();

    String getStateName();

    boolean isOpen();

    boolean isBinding();

    boolean isBound();

    boolean isUnbinding();

    boolean isClosed();

    void setConfiguration(SmppSessionConfiguration configuration);

    SmppSessionConfiguration getConfiguration();

    Channel getChannel();

    AsyncWindow getSendWindow();
}
