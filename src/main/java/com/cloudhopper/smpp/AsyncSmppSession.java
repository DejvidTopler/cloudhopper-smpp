package com.cloudhopper.smpp;

import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface AsyncSmppSession {


    void destroy();

    void sendRequestPdu(PduRequest pdu, PduSentCallback callback);

    void unbind(PduSentCallback callback);

    void sendResponsePdu(PduResponse pdu);

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

    Window<Integer, PduRequest, PduResponse> getSendWindow();

}
