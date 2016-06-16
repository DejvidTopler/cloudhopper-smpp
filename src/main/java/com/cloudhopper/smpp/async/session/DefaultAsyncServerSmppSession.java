package com.cloudhopper.smpp.async.session;


import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.Pdu;
import org.jboss.netty.channel.Channel;

public class DefaultAsyncServerSmppSession extends AbstractAsyncSmppSession {

    public DefaultAsyncServerSmppSession(SmppSessionConfiguration configuration, Channel channel,
            EventDispatcher eventDispatcher) {
        super(configuration, channel, eventDispatcher);
    }

    @Override
    protected boolean isSessionReadyForSubmit(Pdu pdu, State state) {
        if (pdu instanceof BaseBindResp)
            return !State.CLOSED.equals(state);
        return State.BOUND.equals(state);
    }
}
