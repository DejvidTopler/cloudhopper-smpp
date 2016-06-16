package com.cloudhopper.smpp.async.server;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.events.PduRequestReceivedEvent;
import com.cloudhopper.smpp.async.events.handler.EventHandler;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;
import com.cloudhopper.smpp.async.session.DefaultAsyncServerSmppSession;
import com.cloudhopper.smpp.channel.ChannelUtil;
import com.cloudhopper.smpp.channel.SmppChannelConstants;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.LoggingOptions;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

/**
 * Created by ib-dtopler on 6/15/16.
 */
public class DefaultAsyncSmppServer implements AsyncSmppServer {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAsyncSmppServer.class);

    private final EventDispatcher eventDispatcher;
    private final ChannelGroup channels;
    private final ConcurrentMap<Channel, DefaultAsyncServerSmppSession> sessions = new ConcurrentHashMap<>();
    private final SmppServerConnector serverConnector;
    private final SmppServerConfiguration configuration;
    private final PduTranscoder transcoder;
    private ChannelFactory channelFactory;
    private ServerBootstrap serverBootstrap;
    private Channel serverChannel;
    private final org.jboss.netty.util.Timer writeTimeoutTimer;
    private final Timer bindTimer;
    private DefaultSmppServerCounters counters;
    private final AsyncSmppServerAuthentication authentication;

    public DefaultAsyncSmppServer(final SmppServerConfiguration configuration, ExecutorService selectorExecutor,
            ExecutorService workerExecutor, EventDispatcher eventDispatcher,
            AsyncSmppServerAuthentication authentication, int workerExecutorSize) {
        this.configuration = configuration;
        this.eventDispatcher = eventDispatcher;
        this.channels = new DefaultChannelGroup();
        this.channelFactory = new NioServerSocketChannelFactory(
                new NioServerBossPool(selectorExecutor, 1, (currentThreadName, proposedThreadName) -> "SmppServerSelectorThread"),
                new NioWorkerPool(workerExecutor, workerExecutorSize, (currentThreadName, proposedThreadName) -> "SmppServerWorkerThread"));

        this.serverBootstrap = new ServerBootstrap(this.channelFactory);
        this.serverBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        this.serverConnector = new SmppServerConnector(channels, this, eventDispatcher);
        this.serverBootstrap.getPipeline().addLast(SmppChannelConstants.PIPELINE_SERVER_CONNECTOR_NAME, this.serverConnector);
        this.writeTimeoutTimer = new org.jboss.netty.util.HashedWheelTimer();
        this.bindTimer = new Timer(configuration.getName() + "-BindTimer0", true);
        this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext());
        this.counters = new DefaultSmppServerCounters();
        this.authentication = authentication;

        registerBindHandlers();
    }

    private EventHandler<PduRequestReceivedEvent> bindEventHandler = new EventHandler<PduRequestReceivedEvent>() {
        @Override
        public boolean canHandle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
            return sessionEvent.getPduRequest() instanceof BaseBind;
        }

        @Override
        public void handle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
            if (authentication.isValidConnection(sessionEvent, session)) {
                session.setBound();
                session.setConfiguration(createConfiguration((BaseBind) sessionEvent.getPduRequest(), session.getChannel()));
                return;
            }

            sessionEvent.getPduResponse().setCommandStatus(SmppConstants.STATUS_BINDFAIL);
        }
    };

    private SmppSessionConfiguration createConfiguration(BaseBind bindRequest, Channel channel) {
        SmppSessionConfiguration sessionConfiguration = new SmppSessionConfiguration();
        sessionConfiguration.setName("SmppServerSession." + bindRequest.getSystemId() + "." + bindRequest.getSystemType());
        sessionConfiguration.setSystemId(bindRequest.getSystemId());
        sessionConfiguration.setPassword(bindRequest.getPassword());
        sessionConfiguration.setSystemType(bindRequest.getSystemType());
        sessionConfiguration.setBindTimeout(getConfiguration().getBindTimeout());
        sessionConfiguration.setAddressRange(bindRequest.getAddressRange());
        sessionConfiguration.setHost(ChannelUtil.getChannelRemoteHost(channel));
        sessionConfiguration.setPort(ChannelUtil.getChannelRemotePort(channel));
        sessionConfiguration.setInterfaceVersion(bindRequest.getInterfaceVersion());

        LoggingOptions loggingOptions = new LoggingOptions();
        loggingOptions.setLogPdu(true);
        sessionConfiguration.setLoggingOptions(loggingOptions);

        // handle all 3 types...
        if (bindRequest instanceof BindTransceiver) {
            sessionConfiguration.setType(SmppBindType.TRANSCEIVER);
        } else if (bindRequest instanceof BindReceiver) {
            sessionConfiguration.setType(SmppBindType.RECEIVER);
        } else if (bindRequest instanceof BindTransmitter) {
            sessionConfiguration.setType(SmppBindType.TRANSMITTER);
        }

        // new default options set from server config
        sessionConfiguration.setWindowSize(getConfiguration().getDefaultWindowSize());
        sessionConfiguration.setWindowWaitTimeout(getConfiguration().getDefaultWindowWaitTimeout());
        sessionConfiguration.setWindowMonitorInterval(getConfiguration().getDefaultWindowMonitorInterval());
        sessionConfiguration.setRequestExpiryTimeout(getConfiguration().getDefaultRequestExpiryTimeout());
        sessionConfiguration.setCountersEnabled(getConfiguration().isDefaultSessionCountersEnabled());

        return sessionConfiguration;
    }

    private EventHandler<PduRequestReceivedEvent> sessionStateHandler = new EventHandler<PduRequestReceivedEvent>() {
        @Override
        public boolean canHandle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
            return !session.isBound() && !(sessionEvent.getPduRequest() instanceof BaseBind);
        }

        @Override
        public void handle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
            sessionEvent.setStopExecution(true);
            //TODO log invalid pdu received when session was not bound
        }
    };

    private void registerBindHandlers() {
        eventDispatcher.addHandler(PduRequestReceivedEvent.class, sessionStateHandler, EventDispatcher.ExecutionOrder.BEFORE);
        eventDispatcher.addHandler(PduRequestReceivedEvent.class, bindEventHandler, EventDispatcher.ExecutionOrder.AFTER);
    }

    public PduTranscoder getTranscoder() {
        return this.transcoder;
    }

    @Override
    public ConcurrentMap<Channel, DefaultAsyncServerSmppSession> getSessions() {
        return sessions;
    }

    @Override
    public ChannelGroup getChannels() {
        return this.channels;
    }

    public SmppServerConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public DefaultSmppServerCounters getCounters() {
        return this.counters;
    }

    @Override
    public boolean isStarted() {
        return (this.serverChannel != null && this.serverChannel.isBound());
    }

    @Override
    public boolean isStopped() {
        return (this.serverChannel == null);
    }

    @Override
    public boolean isDestroyed() {
        return (this.serverBootstrap == null);
    }

    @Override
    public int getConnectionSize() {
        return channels.size();
    }


    @Override
    public EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    @Override
    public void start() throws SmppChannelException {
        if (isDestroyed()) {
            throw new SmppChannelException("Unable to start: server is destroyed");
        }
        try {
            serverChannel = this.serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            logger.info("{} started at {}:{}", configuration.getName(), configuration.getHost(), configuration.getPort());
        } catch (ChannelException e) {
            throw new SmppChannelException(e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (this.channels.size() > 0) {
            logger.info("{} currently has [{}] open child channel(s) that will be closed as part of stop()", configuration.getName(), this.channels.size());
        }
        // close all channels still open within this session "bootstrap"
        this.channels.close().awaitUninterruptibly();
        // clean up all external resources
        if (this.serverChannel != null) {
            this.serverChannel.close().awaitUninterruptibly();
            this.serverChannel = null;
        }
        logger.info("{} stopped at {}:{}", configuration.getName(), configuration.getHost(), configuration.getPort());
    }

    @Override
    public void destroy() {
        this.bindTimer.cancel();
        stop();
        this.serverBootstrap.releaseExternalResources();
        this.serverBootstrap = null;
        this.writeTimeoutTimer.stop();
        logger.info("{} destroyed on SMPP port [{}]", configuration.getName(), configuration.getPort());
    }

}
