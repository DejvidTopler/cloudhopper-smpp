package com.cloudhopper.smpp.async.session;

import com.cloudhopper.smpp.AsyncClientSmppSession;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import org.jboss.netty.channel.Channel;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class DefaultSessionContextFactory implements SessionContextFactory {
    @Override
    public AsyncClientSmppSession createSession(SmppSession.Type type, SmppSessionConfiguration config, Channel channel,
            EventDispatcher dispatcher) {
        return new DefaultAsyncClientSmppSession(config, channel, dispatcher);
    }
}
