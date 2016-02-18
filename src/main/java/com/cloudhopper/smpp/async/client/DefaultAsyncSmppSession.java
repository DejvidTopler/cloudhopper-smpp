package com.cloudhopper.smpp.async.client;


import com.cloudhopper.commons.util.PeriodFormatterUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.async.AsyncWindow;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.events.*;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.exception.InvalidSessionStateException;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.tlv.TlvConvertException;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import com.cloudhopper.smpp.util.SequenceNumber;
import com.cloudhopper.smpp.util.SmppUtil;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of either an ESME or SMSC SMPP session.
 *
 * @author joelauer (twitter: @jjlauer or <a href="http://twitter.com/jjlauer" target=window>http://twitter.com/jjlauer</a>)
 */
public class DefaultAsyncSmppSession implements AsyncSmppSession {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAsyncSmppSession.class);

    public enum State {
        INITIAL,
        OPEN,
        BINDING,
        BOUND,
        UNBINDING,
        CLOSED
    }

    // current state of this session
    private final AtomicReference<State> state;
    // the timestamp when we became "bound"
    private final AtomicLong boundTime;
    private final SmppSessionConfiguration configuration;
    private final Channel channel;
    private SmppSessionHandler sessionHandler;
    private final SequenceNumber sequenceNumber;
    private final PduTranscoder transcoder;
    private final AsyncWindow window;
    private byte interfaceVersion;
    private final EventDispatcher eventDispatcher;


    /**
     * Creates an SmppSession for a client-based session. It is <b>NOT</b>
     * recommended that this constructor is called directly.  The recommended
     * way to construct a session is either via a DefaultSmppClient or
     * DefaultSmppServer.
     *
     * @param configuration   The session configuration
     * @param channel         The channel associated with this session. The channel
     * @param eventDispatcher
     */
    public DefaultAsyncSmppSession(SmppSessionConfiguration configuration, Channel channel,
            EventDispatcher eventDispatcher) {
        this.state = new AtomicReference<>(State.OPEN);
        this.configuration = configuration;
        this.channel = channel;
        this.boundTime = new AtomicLong(0);
        this.sessionHandler = (sessionHandler == null ? new DefaultSmppSessionHandler(logger) : sessionHandler);
        this.sequenceNumber = new SequenceNumber();
        // always "wrap" the custom pdu transcoder context with a default one
        this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext(this.sessionHandler));
        window = new AsyncWindow();

        // these server-only items are null
        this.eventDispatcher = eventDispatcher;
    }

    public void registerMBean(String objectName) {
        // register the this queue manager as an mbean
        try {
            ObjectName name = new ObjectName(objectName);
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, name);
        } catch (Exception e) {
            // log the error, but don't throw an exception for this datasource
            logger.error("Unable to register DefaultSmppSessionMXBean [{}]", objectName, e);
        }
    }

    public void unregisterMBean(String objectName) {
        // register the this queue manager as an mbean
        try {
            ObjectName name = new ObjectName(objectName);
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
        } catch (Exception e) {
            // log the error, but don't throw an exception for this datasource
            logger.error("Unable to unregister DefaultSmppServerMXBean [{}]", objectName, e);
        }
    }

    @Override
    public SmppBindType getBindType() {
        return this.configuration.getType();
    }

    private void setBound() {
        this.state.set(State.BOUND);
        this.boundTime.set(System.currentTimeMillis());
    }

    @Override
    public long getBoundTime() {
        return this.boundTime.get();
    }

    @Override
    public String getStateName() {
        return this.state.get().name();
    }

    protected void setInterfaceVersion(byte value) {
        this.interfaceVersion = value;
    }

    public byte getInterfaceVersion() {
        return this.interfaceVersion;
    }

    @Override
    public boolean isOpen() {
        return State.OPEN.equals(this.state.get());
    }

    @Override
    public boolean isBinding() {
        return State.BINDING.equals(this.state.get());
    }

    @Override
    public boolean isBound() {
        return State.BOUND.equals(this.state.get());
    }

    @Override
    public boolean isUnbinding() {
        return State.UNBINDING.equals(this.state.get());
    }

    @Override
    public boolean isClosed() {
        return State.CLOSED.equals(this.state.get());
    }

    @Override
    public SmppSessionConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public Channel getChannel() {
        return this.channel;
    }

    public SequenceNumber getSequenceNumber() {
        return this.sequenceNumber;
    }

    @Override
    public AsyncWindow getSendWindow() {
        return this.window;
    }

    @Override
    public void bind(BaseBind request, BindCallback bindCallback) {
        if (!this.state.compareAndSet(State.OPEN, State.BINDING) && !this.state.compareAndSet(State.CLOSED, State.BINDING)) {
            bindCallback.onFailure(BindCallback.Reason.INVALID_SESSION_STATE, null, null);
            return;
        }

        AsyncRequestContext ctx = new AsyncRequestContext(request, null, new PduSentCallback<BaseBindResp>() {
            @Override
            public void onSuccess(BaseBindResp bindResp) {
                if (bindResp.getCommandStatus() != SmppConstants.STATUS_OK)
                    DefaultAsyncSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.NEGATIVE_BIND_RESP, null, bindResp));
                else {
                    negotiateServerVersion(bindResp);
                    setBound();
                    bindCallback.onBindSucess(DefaultAsyncSmppSession.this);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                DefaultAsyncSmppSession.this.close(future -> {
                    if (t instanceof RecoverablePduException || t instanceof UnrecoverablePduException || t instanceof SmppTimeoutException ||
                            t instanceof SmppChannelException || t instanceof InterruptedException)
                        bindCallback.onFailure(BindCallback.Reason.SEND_BIND_REQ_FAILED, t, null);
                    else
                        bindCallback.onFailure(BindCallback.Reason.READ_ERROR, t, null);
                });

            }

            @Override
            public void onExpire() {
                DefaultAsyncSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.READ_TIMEOUT, new ReadTimeoutException("Request expire in window"), null));
            }

            @Override
            public void onCancel(CancelReason cancelReason) {
                DefaultAsyncSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.CONNECT_CANCELED, null, null));
            }
        });

        sendRequest(ctx);
    }

    private void negotiateServerVersion(BaseBindResp bindResponse) {
        Tlv scInterfaceVersion = bindResponse.getOptionalParameter(SmppConstants.TAG_SC_INTERFACE_VERSION);

        if (scInterfaceVersion == null) {
            // this means version 3.3 is in use
            this.interfaceVersion = SmppConstants.VERSION_3_3;
        } else {
            try {
                byte tempInterfaceVersion = scInterfaceVersion.getValueAsByte();
                if (tempInterfaceVersion >= SmppConstants.VERSION_3_4) {
                    this.interfaceVersion = SmppConstants.VERSION_3_4;
                } else {
                    this.interfaceVersion = SmppConstants.VERSION_3_3;
                }
            } catch (TlvConvertException e) {
                logger.warn("Unable to convert sc_interface_version to a byte value: {}", e.getMessage());
                this.interfaceVersion = SmppConstants.VERSION_3_3;
            }
        }
    }

    @Override
    public void unbind(PduSentCallback callback) {
        unbind(callback, configuration.getRequestExpiryTimeout());
    }

    @Override
    public void unbind(PduSentCallback callback, long windowTimeout) {
        if (callback == null)
            throw new NullPointerException("Unbind callback can't be null");

        this.state.set(State.UNBINDING);

        AsyncRequestContext ctx = new AsyncRequestContext(new Unbind(), this, new PduSentCallback<UnbindResp>() {
            @Override
            public void onSuccess(UnbindResp response) {
                close(future -> callback.onSuccess(response));
            }

            @Override
            public void onFailure(Throwable t) {
                close(future -> callback.onFailure(t));
            }

            @Override
            public void onExpire() {
                close(future -> callback.onExpire());
            }

            @Override
            public void onCancel(CancelReason cancelReason) {
                close(future -> callback.onCancel(cancelReason));
            }
        });

        ctx.setWindowTimeout(windowTimeout);
        sendRequest(ctx);
    }

    private void close(ChannelFutureListener listener) {
        this.channel.close().addListener(future -> {
            this.state.set(State.CLOSED);
            if (listener != null)
                listener.operationComplete(future);
        });
    }

    @Override
    public void destroy() {
        this.state.set(State.UNBINDING);
        close(null);
        List<AsyncRequestContext> canceled = window.cancelAll();
        for (AsyncRequestContext ctx : canceled) {
            PduSentCallback callback = ctx.getCallback();
            if (callback != null)
                callback.onCancel(PduSentCallback.CancelReason.DESTROY);
        }
    }

    @Override
    public void sendRequest(AsyncRequestContext ctx) {
        if (!isSessionReadyForSubmit(ctx)) return;

        if (eventDispatcher.hasHandlers(BeforePduRequestSentEvent.class)) {
            BeforePduRequestSentEvent event = eventDispatcher.dispatch(new BeforePduRequestSentEvent(ctx), this);
            if (event.isStopExecution()) {
                PduSentCallback callback = ctx.getCallback();
                if (callback != null)
                    callback.onCancel(PduSentCallback.CancelReason.STOPPED_EXECUTION);
                return;
            }
        }

        try {
            sendRequestPduInternal(ctx);
        } catch (Throwable e) {
            PduSentCallback callback = ctx.getCallback();
            if (callback != null)
                callback.onFailure(e);
        }
    }

    private boolean isSessionReadyForSubmit(AsyncRequestContext ctx) {
        PduRequest pdu = ctx.getRequest();
        if (pdu instanceof BaseBind) {
            return true;
        }

        if (pdu instanceof Unbind) {
            return true;
        }

        if (!State.BOUND.equals(this.state.get())) {
            PduSentCallback callback = ctx.getCallback();
            if (callback != null)
                callback.onCancel(PduSentCallback.CancelReason.INVALID_SESSION_STATE);
            return false;
        }

        return true;
    }

    private void sendRequestPduInternal(AsyncRequestContext ctx) throws Exception {
        PduRequest pdu = ctx.getRequest();
        // assign the next PDU sequence # if its not yet assigned
        if (!pdu.hasSequenceNumberAssigned()) {
            pdu.setSequenceNumber(this.sequenceNumber.next());
        }

        if (ctx.getWindowTimeout() == 0L) {
            ctx.setWindowTimeout(configuration.getRequestExpiryTimeout());
        }

        // encode the pdu into a buffer
        ChannelBuffer buffer = transcoder.encode(pdu);

        window.insert(ctx);

        ChannelFuture channelFuture;
        try {
            channelFuture = this.channel.write(buffer);
        } catch (Throwable t){
            window.complete(ctx.getRequest().getSequenceNumber());
            throw t;
        }

        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                if (eventDispatcher.hasHandlers(PduRequestSentEvent.class)) {
                    eventDispatcher.dispatch(new PduRequestSentEvent(ctx), this);
                }
            } else {
                window.complete(ctx.getRequest().getSequenceNumber());
                PduSentCallback callback = ctx.getCallback();
                if (callback != null)
                    callback.onFailure(f.getCause());
            }
        });
    }

    @Override
    public void sendResponsePdu(PduResponse pdu) {
        if (!State.BOUND.equals(state.get())) {
            if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class)) {
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, new InvalidSessionStateException()), DefaultAsyncSmppSession.this);
            }
            return;
        }

        if (eventDispatcher.hasHandlers(BeforePduResponseSentEvent.class)) {
            BeforePduResponseSentEvent event = new BeforePduResponseSentEvent(pdu);
            eventDispatcher.dispatch(event, this);
            if (event.isStopExecution()) {
                return;
            }
        }

        if (!pdu.hasSequenceNumberAssigned()) {
            pdu.setSequenceNumber(this.sequenceNumber.next());
        }

        ChannelBuffer buffer;
        try {
            buffer = transcoder.encode(pdu);
        } catch (Exception e) {
            if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class)) {
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, e), DefaultAsyncSmppSession.this);
            }

            return;
        }

        // write the pdu out & wait timeout amount of time
        ChannelFuture channelFuture;
        try {
            channelFuture = this.channel.write(buffer);
        } catch (Throwable t) {
            if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class))
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, t.getCause()), DefaultAsyncSmppSession.this);

            return;
        }

        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                if (eventDispatcher.hasHandlers(PduResponseSentEvent.class)) {
                    eventDispatcher.dispatch(new PduResponseSentEvent(pdu), DefaultAsyncSmppSession.this);
                }
            } else {
                if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class)) {
                    eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, f.getCause()), DefaultAsyncSmppSession.this);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void firePduReceived(Pdu pdu) {
        if (pdu instanceof PduRequest) {
            PduRequestReceivedEvent event;
            if (eventDispatcher.hasHandlers(PduRequestReceivedEvent.class)) {
                event = eventDispatcher.dispatch(new PduRequestReceivedEvent((PduRequest) pdu), this);
                if (event.isStopExecution())
                    return;
            }

            sendResponsePdu(((PduRequest) pdu).createResponse());
            if (pdu instanceof Unbind) {
                destroy();
            }
        } else if (pdu instanceof PduResponse) {
            PduResponse pduResponse = (PduResponse) pdu;
            AsyncRequestContext ctx = window.complete(pduResponse.getSequenceNumber());

            if (ctx != null) {
                if (eventDispatcher.hasHandlers(PduResponseReceivedEvent.class))
                    eventDispatcher.dispatch(new PduResponseReceivedEvent(ctx, pduResponse), this);

                PduSentCallback callback = ctx.getCallback();
                if(callback != null)
                    callback.onSuccess(pduResponse);

            } else {
                if (eventDispatcher.hasHandlers(UnexpectedPduResponseReceivedEvent.class))
                    eventDispatcher.dispatch(new UnexpectedPduResponseReceivedEvent(pduResponse), this);
            }
        } else {
            if (eventDispatcher.hasHandlers(AlertNotificationReceivedEvent.class)) {
                eventDispatcher.dispatch(new AlertNotificationReceivedEvent((AlertNotification) pdu), this);
            }
        }
    }

    @Override
    public void fireExceptionThrown(Throwable t) {
        if (!eventDispatcher.hasHandlers(ExceptionThrownEvent.class))
            return;

        eventDispatcher.dispatch(new ExceptionThrownEvent(t), this);
    }

    @Override
    public void fireChannelClosed() {
        if (eventDispatcher.hasHandlers(ChannelClosedEvent.class)) {
            eventDispatcher.dispatch(new ChannelClosedEvent(), this);
        }

        destroy();
    }

    public String getBindTypeName() {
        return this.getBindType().toString();
    }

    public String getBoundDuration() {
        return PeriodFormatterUtil.toLinuxUptimeStyleString(System.currentTimeMillis() - getBoundTime());
    }

    public String getInterfaceVersionName() {
        return SmppUtil.toInterfaceVersionString(interfaceVersion);
    }

    public int getNextSequenceNumber() {
        return this.sequenceNumber.peek();
    }

    public String getLocalAddressAndPort() {
        if (this.channel != null) {
            InetSocketAddress addr = (InetSocketAddress) this.channel.getLocalAddress();
            return addr.getAddress().getHostAddress() + ":" + addr.getPort();
        } else {
            return null;
        }
    }

    public String getRemoteAddressAndPort() {
        if (this.channel != null) {
            InetSocketAddress addr = (InetSocketAddress) this.channel.getRemoteAddress();
            return addr.getAddress().getHostAddress() + ":" + addr.getPort();
        } else {
            return null;
        }
    }

    public String getName() {
        return this.configuration.getName();
    }

    public String getPassword() {
        return this.configuration.getPassword();
    }

    public long getRequestExpiryTimeout() {
        return this.configuration.getRequestExpiryTimeout();
    }

    public String getSystemId() {
        return this.configuration.getSystemId();
    }

    public String getSystemType() {
        return this.configuration.getSystemType();
    }

    public int getWindowSize() {
        return this.window.getSize();
    }

}
