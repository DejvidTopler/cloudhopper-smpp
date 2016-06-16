package com.cloudhopper.smpp.async.session;

import com.cloudhopper.commons.util.PeriodFormatterUtil;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppSessionHandler;
import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.async.AsyncWindow;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.events.*;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.exception.InvalidSessionStateException;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.util.SequenceNumber;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ib-dtopler on 6/15/16.
 */
public abstract class AbstractAsyncSmppSession implements AsyncSmppSession {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAsyncClientSmppSession.class);

    // current state of this session
    protected final AtomicReference<State> state;
    // the timestamp when we became "bound"
    private final AtomicLong boundTime;
    protected SmppSessionConfiguration configuration;
    private final Channel channel;
    private SmppSessionHandler sessionHandler;
    private final SequenceNumber sequenceNumber;
    private final PduTranscoder transcoder;
    private final AsyncWindow window;
    protected final EventDispatcher eventDispatcher;


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
    public AbstractAsyncSmppSession(SmppSessionConfiguration configuration, Channel channel,
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

    public SmppBindType getBindType() {
        return this.configuration.getType();
    }

    @Override
    public void setBound() {
        this.state.set(State.BOUND);
        this.boundTime.set(System.currentTimeMillis());
    }

    public long getBoundTime() {
        return this.boundTime.get();
    }

    public String getStateName() {
        return this.state.get().name();
    }

    public boolean isOpen() {
        return State.OPEN.equals(this.state.get());
    }

    public boolean isBinding() {
        return State.BINDING.equals(this.state.get());
    }

    public boolean isBound() {
        return State.BOUND.equals(this.state.get());
    }

    public boolean isUnbinding() {
        return State.UNBINDING.equals(this.state.get());
    }

    public boolean isClosed() {
        return State.CLOSED.equals(this.state.get());
    }

    public SmppSessionConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void setConfiguration(SmppSessionConfiguration configuration) {
        this.configuration = configuration;
    }

    public Channel getChannel() {
        return this.channel;
    }

    public AsyncWindow getSendWindow() {
        return this.window;
    }

    public void unbind(PduSentCallback callback) {
        unbind(callback, configuration.getBindTimeout());
    }

    public SequenceNumber getSequenceNumber() {
        return this.sequenceNumber;
    }

    public String getBindTypeName() {
        return this.getBindType().toString();
    }

    public String getBoundDuration() {
        return PeriodFormatterUtil.toLinuxUptimeStyleString(System.currentTimeMillis() - getBoundTime());
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

    protected void close(ChannelFutureListener listener) {
        this.channel.close().addListener(future -> {
            this.state.set(State.CLOSED);
            if (listener != null)
                listener.operationComplete(future);
        });
    }

    public void destroy() {
        this.state.set(State.UNBINDING);
        close(null);
        window.cancelAll(PduSentCallback.CancelReason.DESTROY);
    }

    public void sendRequest(AsyncRequestContext ctx) {
        if (!isSessionReadyForSubmit(ctx.getRequest(), this.state.get())) {
            cancelRequest(ctx, PduSentCallback.CancelReason.INVALID_SESSION_STATE);
            return;
        }

        if (eventDispatcher.hasHandlers(BeforePduRequestSentEvent.class)) {
            BeforePduRequestSentEvent event = eventDispatcher.dispatch(new BeforePduRequestSentEvent(ctx), this);
            if (event.isStopExecution()) {
                cancelRequest(ctx, PduSentCallback.CancelReason.STOPPED_EXECUTION);
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

    private void cancelRequest(AsyncRequestContext ctx, PduSentCallback.CancelReason invalidSessionState) {
        PduSentCallback callback = ctx.getCallback();
        if (callback != null)
            callback.onCancel(invalidSessionState);
    }

    protected abstract boolean isSessionReadyForSubmit(Pdu pdu, State state);

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
        } catch (Throwable t) {
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

    public void sendResponsePdu(PduResponse pdu) {
        if(!isSessionReadyForSubmit(pdu, state.get())){
            if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class)) {
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, new InvalidSessionStateException()), AbstractAsyncSmppSession.this);
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
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, e), AbstractAsyncSmppSession.this);
            }

            return;
        }

        // write the pdu out & wait timeout amount of time
        ChannelFuture channelFuture;
        try {
            channelFuture = this.channel.write(buffer);
        } catch (Throwable t) {
            if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class))
                eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, t.getCause()), AbstractAsyncSmppSession.this);

            return;
        }

        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                if (eventDispatcher.hasHandlers(PduResponseSentEvent.class)) {
                    eventDispatcher.dispatch(new PduResponseSentEvent(pdu), AbstractAsyncSmppSession.this);
                }
            } else {
                if (eventDispatcher.hasHandlers(PduResponseSendFailedEvent.class)) {
                    eventDispatcher.dispatch(new PduResponseSendFailedEvent(pdu, f.getCause()), AbstractAsyncSmppSession.this);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void firePduReceived(Pdu pdu) {
        if (pdu instanceof PduRequest) {
            PduRequestReceivedEvent event = null;
            if (eventDispatcher.hasHandlers(PduRequestReceivedEvent.class)) {
                event = eventDispatcher.dispatch(new PduRequestReceivedEvent((PduRequest) pdu, ((PduRequest) pdu).createResponse()), this);
                if (event.isStopExecution())
                    return;
            }

            PduResponse response = event != null ? event.getPduResponse() : ((PduRequest) pdu).createResponse();
            sendResponsePdu(response);
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
                if (callback != null)
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

    public enum State {
        INITIAL,
        OPEN,
        BINDING,
        BOUND,
        UNBINDING,
        CLOSED
    }
}
