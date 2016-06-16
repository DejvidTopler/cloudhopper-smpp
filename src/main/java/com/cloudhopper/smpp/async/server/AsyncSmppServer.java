package com.cloudhopper.smpp.async.server;

import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.session.DefaultAsyncServerSmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;

import java.util.concurrent.ConcurrentMap;

/**
 * Created by ib-dtopler on 6/15/16.
 */
public interface AsyncSmppServer {

    EventDispatcher getEventDispatcher();

    void start() throws SmppChannelException;

    void stop();

    void destroy();

    ConcurrentMap<Channel, DefaultAsyncServerSmppSession> getSessions();

    ChannelGroup getChannels();

    DefaultSmppServerCounters getCounters();

    boolean isStarted();

    boolean isStopped();

    boolean isDestroyed();

    int getConnectionSize();
}
