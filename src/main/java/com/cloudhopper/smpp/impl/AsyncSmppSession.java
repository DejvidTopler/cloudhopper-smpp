package com.cloudhopper.smpp.impl;

import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface AsyncSmppSession {

    void unbind(UnbindCallback callback);

    void destroy();

    void sendRequestPdu(PduRequest pdu, PduSentCallback callback);

    void sendResponsePdu(PduResponse pdu) throws RecoverablePduException, UnrecoverablePduException,
            SmppChannelException, InterruptedException;


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
