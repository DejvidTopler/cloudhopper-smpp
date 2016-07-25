package com.cloudhopper.smpp.async.client;

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

import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.events.support.EventDispatcherImpl;
import com.cloudhopper.smpp.channel.*;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BindReceiver;
import com.cloudhopper.smpp.pdu.BindTransceiver;
import com.cloudhopper.smpp.pdu.BindTransmitter;
import com.cloudhopper.smpp.ssl.SslConfiguration;
import com.cloudhopper.smpp.ssl.SslContextFactory;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.type.SmppChannelConnectException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation to "bootstrap" client SMPP sessions (create & bind).
 *
 * @author joelauer (twitter: @jjlauer or <a href="http://twitter.com/jjlauer" target=window>http://twitter.com/jjlauer</a>)
 */
public class DefaultAsyncSmppClient implements AsyncSmppClient {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAsyncSmppClient.class);

    private final EventDispatcher eventDispatcher;
    private final ChannelGroup channels;
    private final SmppClientConnector clientConnector;
    private final ExecutorService selectorExecutors;
    private final ExecutorService executors;
    private final ClientSocketChannelFactory channelFactory;
    private final ClientBootstrap clientBootstrap;
    private final DefaultPduTranscoder transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());

    /**
     * @param executors        used for worker pool
     * @param expectedSessions is maxPoolSize for executors
     */
    public DefaultAsyncSmppClient(ExecutorService executors, ExecutorService selectorExecutors, int expectedSessions) {
        this.channels = new DefaultChannelGroup();
        this.executors = executors;
        this.selectorExecutors = selectorExecutors;
        this.channelFactory = new NioClientSocketChannelFactory(
                new NioClientBossPool(this.selectorExecutors, 1, new HashedWheelTimer(), (currentThreadName, proposedThreadName) -> "SmppClientSelectorThread"),
                createWorkerPool(expectedSessions));
        this.clientBootstrap = new ClientBootstrap(channelFactory);
        this.clientConnector = new SmppClientConnector(this.channels);
        this.clientBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_CLIENT_CONNECTOR_NAME, this.clientConnector);
        this.eventDispatcher = new EventDispatcherImpl();
    }

    private NioWorkerPool createWorkerPool(int expectedSessions) {
        AtomicInteger count = new AtomicInteger();
        return new NioWorkerPool(this.executors, expectedSessions, (currThrName, proposedThrName) -> "SmppClientWorkerThread-" + count.incrementAndGet());
    }

    @Override
    public EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    @Override
    public int getConnectionSize() {
        return this.channels.size();
    }

    @Override
    public void destroy() {
        // close all channels still open within this session "bootstrap"
        this.channels.close().awaitUninterruptibly();
        // clean up all external resources
        this.clientBootstrap.releaseExternalResources();
    }

    private BaseBind createBindRequest(SmppSessionConfiguration config) throws UnrecoverablePduException {
        BaseBind bind;
        if (config.getType() == SmppBindType.TRANSCEIVER) {
            bind = new BindTransceiver();
        } else if (config.getType() == SmppBindType.RECEIVER) {
            bind = new BindReceiver();
        } else if (config.getType() == SmppBindType.TRANSMITTER) {
            bind = new BindTransmitter();
        } else {
            throw new UnrecoverablePduException("Unable to convert SmppSessionConfiguration into a BaseBind request");
        }
        bind.setSystemId(config.getSystemId());
        bind.setPassword(config.getPassword());
        bind.setSystemType(config.getSystemType());
        bind.setInterfaceVersion(config.getInterfaceVersion());
        bind.setAddressRange(config.getAddressRange());
        return bind;
    }

    @Override
    public void bind(SmppSessionConfiguration config, BindCallback bindCallback) {
        bind(config, bindCallback, null);
    }

    @Override
    public void bind(SmppSessionConfiguration config, BindCallback bindCallback,
            SessionContextFactory sessionContextFactory) {
        if (bindCallback == null) {
            throw new NullPointerException("AsyncBindCallback can not be null.");
        }

        ChannelFutureListener callback = connectFuture -> {
            if (connectFuture.isCancelled()) {
                bindCallback.onFailure(BindCallback.Reason.CONNECT_CANCELED, null, null);
            } else if (!connectFuture.isSuccess()) {
                if (connectFuture.getCause() instanceof org.jboss.netty.channel.ConnectTimeoutException) {
                    bindCallback.onFailure(BindCallback.Reason.CONNECT_TIMEOUT, connectFuture.getCause(), null);
                } else {
                    bindCallback.onFailure(BindCallback.Reason.CONNECTION_REFUSED, connectFuture.getCause(), null);
                }
            } else {
                // if we get here, then we were able to connect and get a channel
                Channel channel = connectFuture.getChannel();

                try {
                    AsyncSmppSession smppSession = createSession(channel, config, sessionContextFactory);
                    bindCallback.onSessionCreate(smppSession);

                    BaseBind bindRequest = createBindRequest(config);
                    smppSession.bind(bindRequest, bindCallback);
                } catch (SmppTimeoutException | SmppChannelException | InterruptedException t) {
                    bindCallback.onFailure(BindCallback.Reason.SSL_FAILURE, t, null);
                } catch (UnrecoverablePduException e) {
                    bindCallback.onFailure(BindCallback.Reason.INVALID_BIND_TYPE, e, null);
                } catch (Throwable t) {
                    bindCallback.onFailure(BindCallback.Reason.UNKNOWN, t, null);
                }

            }
        };

        createConnectedChannel(config.getHost(), config.getPort(), config.getConnectTimeout(), callback);
    }

    private AsyncSmppSession createSession(Channel channel, SmppSessionConfiguration config,
            SessionContextFactory sessionContextFactory) throws SmppTimeoutException, SmppChannelException, InterruptedException {
        AsyncSmppSession session = sessionContextFactory == null ?
                new DefaultAsyncSmppSession(config, channel, eventDispatcher) :
                sessionContextFactory.createSession(SmppSession.Type.CLIENT, config, channel, eventDispatcher);

        // add SSL handler
        if (config.isUseSsl()) {
            SslConfiguration sslConfig = config.getSslConfiguration();
            if (sslConfig == null) throw new IllegalStateException("sslConfiguration must be set");
            try {
                SslContextFactory factory = new SslContextFactory(sslConfig);
                SSLEngine sslEngine = factory.newSslEngine();
                sslEngine.setUseClientMode(true);
                channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_SSL_NAME, new SslHandler(sslEngine));
            } catch (Exception e) {
                throw new SmppChannelConnectException("Unable to create SSL session]: " + e.getMessage(), e);
            }
        }

        // add the thread renamer portion to the pipeline
        if (config.getName() != null) {
            channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_THREAD_RENAMER_NAME, new SmppSessionThreadRenamer(config.getName()));
        } else {
            logger.warn("Session configuration did not have a name set - skipping threadRenamer in pipeline");
        }

        // create the logging handler (for bytes sent/received on wire)
        SmppSessionLogger loggingHandler = new SmppSessionLogger(DefaultSmppSession.class.getCanonicalName(), config.getLoggingOptions());
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_LOGGER_NAME, loggingHandler);

        // add a new instance of a decoder (that takes care of handling frames)
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_PDU_DECODER_NAME, new SmppSessionPduDecoder(transcoder));

        // create a new wrapper around a session to pass the pdu up the chain
        channel.getPipeline().addLast(SmppChannelConstants.PIPELINE_SESSION_WRAPPER_NAME, new SmppSessionWrapper(session));

        return session;
    }

    private void createConnectedChannel(String host, int port, long connectTimeoutMillis,
            ChannelFutureListener channelFutureListener) {
        // a socket address used to "bind" to the remote system
        InetSocketAddress socketAddr = new InetSocketAddress(host, port);
        // set the timeout
        this.clientBootstrap.setOption("connectTimeoutMillis", connectTimeoutMillis);

        ChannelFuture connectFuture = this.clientBootstrap.connect(socketAddr);
        connectFuture.addListener(channelFutureListener);
    }

}
