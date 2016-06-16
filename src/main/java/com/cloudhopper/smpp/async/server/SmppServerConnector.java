package com.cloudhopper.smpp.async.server;

/*
 * #%L
 * ch-smpp
 * %%
 * Copyright (C) 2009 - 2015 Cloudhopper by Twitter
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.session.DefaultAsyncServerSmppSession;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.channel.SmppSessionPduDecoder;
import com.cloudhopper.smpp.channel.SmppSessionWrapper;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;

@ChannelPipelineCoverage("all")
public class SmppServerConnector extends SimpleChannelUpstreamHandler {
    private final ChannelGroup channels;
    private final DefaultAsyncSmppServer server;
    private final EventDispatcher eventDispatcher;

    public SmppServerConnector(ChannelGroup channels, DefaultAsyncSmppServer server, EventDispatcher eventDispatcher) {
        this.channels = channels;
        this.server = server;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        Channel channel = e.getChannel();
        channels.add(channel);
        this.server.getCounters().incrementChannelConnectsAndGet();

        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_PDU_DECODER_NAME, new SmppSessionPduDecoder(server.getTranscoder()));

        DefaultAsyncServerSmppSession session = new DefaultAsyncServerSmppSession(null, channel, eventDispatcher);
        this.server.getSessions().put(channel, session);
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME, new SmppSessionWrapper(session));
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // called every time a channel disconnects
        channels.remove(e.getChannel());
        this.server.getSessions().remove(e.getChannel());
        this.server.getCounters().incrementChannelDisconnectsAndGet();
    }

}
