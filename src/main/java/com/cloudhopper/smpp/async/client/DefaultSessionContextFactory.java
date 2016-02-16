package com.cloudhopper.smpp.async.client;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class DefaultSessionContextFactory implements SessionContextFactory {
    @Override
    public AsyncSmppSession createSession(SmppSession.Type type, SmppSessionConfiguration config, Channel channel,
            EventDispatcher dispatcher) {
        return new DefaultAsyncSmppSession(config, channel, dispatcher);
    }
}
