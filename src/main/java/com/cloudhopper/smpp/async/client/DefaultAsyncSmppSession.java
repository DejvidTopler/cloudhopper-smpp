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

import com.cloudhopper.commons.util.PeriodFormatterUtil;
import com.cloudhopper.commons.util.windowing.Window;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.SmppSession.*;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.events.*;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.impl.DefaultSmppSessionCounters;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.impl.DelegatingWindowFutureListener;
import com.cloudhopper.smpp.impl.SmppSessionChannelListener;
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
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.cloudhopper.smpp.SmppSession.*;

/**
 * Default implementation of either an ESME or SMSC SMPP session.
 * 
 * @author joelauer (twitter: @jjlauer or <a href="http://twitter.com/jjlauer" target=window>http://twitter.com/jjlauer</a>)
 */
public class DefaultAsyncSmppSession implements SmppSessionChannelListener, AsyncSmppSession {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAsyncSmppSession.class);

    // are we an "esme" or "smsc" session type?
    private final Type localType;
    // current state of this session
    private final AtomicInteger state;
    // the timestamp when we became "bound"
    private final AtomicLong boundTime;
    private final SmppSessionConfiguration configuration;
    private final Channel channel;
    private SmppSessionHandler sessionHandler;
    private final SequenceNumber sequenceNumber;
    private final PduTranscoder transcoder;
    private final Window<Integer,PduRequest,PduResponse> sendWindow;
    private byte interfaceVersion;
    private DefaultSmppSessionCounters counters;
    private final EventDispatcher eventDispatcher;


    /**
     * Creates an SmppSession for a client-based session. It is <b>NOT</b>
     * recommended that this constructor is called directly.  The recommended
     * way to construct a session is either via a DefaultSmppClient or
     * DefaultSmppServer.
     * @param localType The type of local endpoint (ESME vs. SMSC)
     * @param configuration The session configuration
     * @param channel The channel associated with this session. The channel
     * @param eventDispatcher
     */
    public DefaultAsyncSmppSession(Type localType, SmppSessionConfiguration configuration, Channel channel,
            EventDispatcher eventDispatcher) {
        this.localType = localType;
        this.state = new AtomicInteger(SmppSession.STATE_OPEN);
        this.configuration = configuration;
        this.channel = channel;
        this.boundTime = new AtomicLong(0);
        this.sessionHandler = (sessionHandler == null ? new DefaultSmppSessionHandler(logger) : sessionHandler);
        this.sequenceNumber = new SequenceNumber();
        // always "wrap" the custom pdu transcoder context with a default one
        this.transcoder = new DefaultPduTranscoder(new DefaultPduTranscoderContext(this.sessionHandler));
        this.sendWindow = new Window<>(configuration.getWindowSize());

        // these server-only items are null
        this.eventDispatcher = eventDispatcher;
        if (configuration.isCountersEnabled()) {
            this.counters = new DefaultSmppSessionCounters();
        }
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

    public Type getLocalType() {
        return this.localType;
    }

    public Type getRemoteType() {
        if (this.localType == Type.CLIENT) {
            return Type.SERVER;
        } else {
            return Type.CLIENT;
        }
    }

    private void setBound() {
        this.state.set(STATE_BOUND);
        this.boundTime.set(System.currentTimeMillis());
    }

    @Override
    public long getBoundTime() {
        return this.boundTime.get();
    }

    @Override
    public String getStateName() {
        int s = this.state.get();
        if (s >= 0 || s < STATES.length) {
            return STATES[s];
        } else {
            return "UNKNOWN (" + s + ")";
        }
    }

    protected void setInterfaceVersion(byte value) {
        this.interfaceVersion = value;
    }

    public byte getInterfaceVersion() {
        return this.interfaceVersion;
    }

    public boolean areOptionalParametersSupported() {
        return (this.interfaceVersion >= SmppConstants.VERSION_3_4);
    }

    @Override
    public boolean isOpen() {
        return (this.state.get() == STATE_OPEN);
    }

    @Override
    public boolean isBinding() {
        return (this.state.get() == STATE_BINDING);
    }

    @Override
    public boolean isBound() {
        return (this.state.get() == STATE_BOUND);
    }

    @Override
    public boolean isUnbinding() {
        return (this.state.get() == STATE_UNBINDING);
    }

    @Override
    public boolean isClosed() {
        return (this.state.get() == STATE_CLOSED);
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
    public Window<Integer,PduRequest,PduResponse> getSendWindow() {
        return this.sendWindow;
    }
    
    public boolean hasCounters() {
        return (this.counters != null);
    }
    
    public SmppSessionCounters getCounters() {
        return this.counters;
    }

    protected void bind(BaseBind request, BindCallback bindCallback) {
        this.state.set(STATE_BINDING);
        sendRequestPdu(request, new PduSentCallback<BaseBindResp>() {
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
            public void onCancel() {
                DefaultAsyncSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.CONNECT_CANCELED, null, null));
            }
        });
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
        if(callback == null)
            throw new NullPointerException("Unbind callback can't be null");

        if (this.channel.isConnected())
            this.state.set(STATE_UNBINDING);

        sendRequestPdu(new Unbind(), new PduSentCallback<UnbindResp>() {
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
            public void onCancel() {
                close(future -> callback.onCancel());
            }
        });
    }

    private void close(ChannelFutureListener listener){
        this.channel.close().addListener(future -> {
            this.state.set(STATE_CLOSED);
            if(listener != null)
                listener.operationComplete(future);
        });
    }

    @Override
    public void destroy() {
        if (this.channel.isConnected())
            this.state.set(STATE_UNBINDING);

        close(null);

        DefaultAsyncSmppSession.this.sendWindow.destroy();
    }

    @Override
    public void sendRequestPdu(PduRequest pdu, PduSentCallback callback) {
        if(eventDispatcher.hasHandlers(BeforePduRequestSentEvent.class)) {
            BeforePduRequestSentEvent event = eventDispatcher.dispatch(new BeforePduRequestSentEvent(pdu), this);
            if(event.isStopExecution()){
                callback.onCancel();
                return;
            }
        }

        try {
            WindowFuture<Integer, PduRequest, PduResponse> future = sendRequestPdu(pdu);
            future.addListener(new DelegatingWindowFutureListener<>(callback));
        } catch (Throwable e) {
            callback.onFailure(e);
        }
    }

    private WindowFuture<Integer,PduRequest,PduResponse> sendRequestPdu(PduRequest pdu) throws Exception{
        // assign the next PDU sequence # if its not yet assigned
        if (!pdu.hasSequenceNumberAssigned()) {
            pdu.setSequenceNumber(this.sequenceNumber.next());
        }
        // encode the pdu into a buffer
        ChannelBuffer buffer = transcoder.encode(pdu);

        WindowFuture<Integer,PduRequest,PduResponse> windowFuture = sendWindow.offer(pdu.getSequenceNumber(), pdu, 0, configuration.getRequestExpiryTimeout(), false);

        this.channel.write(buffer).addListener(f -> {
            if (f.isSuccess())
                DefaultAsyncSmppSession.this.countSendRequestPdu(pdu);
            else {
                //there is no point to leave pdu in window to expire, this will also notify all listeners
                windowFuture.fail(f.getCause());
            }
        });

        return windowFuture;
    }

    @Override
    public void sendResponsePdu(PduResponse pdu) {
        // assign the next PDU sequence # if its not yet assigned
        long responseTime = System.currentTimeMillis() - 0; //TODO(DT)
        this.countSendResponsePdu(pdu, responseTime, responseTime);

        if (!pdu.hasSequenceNumberAssigned()) {
            pdu.setSequenceNumber(this.sequenceNumber.next());
        }

        if(eventDispatcher.hasHandlers(BeforePduResponseSentEvent.class)) {
            BeforePduResponseSentEvent event = new BeforePduResponseSentEvent(pdu);
            eventDispatcher.dispatch(event, this);
            if(event.isStopExecution()) {
                return;
            }
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
        this.channel.write(buffer).addListener(f -> {
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
        if(eventDispatcher.hasHandlers(PduReceivedEvent.class)) {
            PduReceivedEvent event = eventDispatcher.dispatch(new PduReceivedEvent(pdu), this);
            if(event.isStopExecution())
                return;
        }

        if (pdu instanceof PduRequest) {
            PduRequestReceivedEvent event;
            if(eventDispatcher.hasHandlers(PduRequestReceivedEvent.class)){
                event = eventDispatcher.dispatch(new PduRequestReceivedEvent((PduRequest) pdu), this);
                if(event.isStopExecution())
                    return;
            }

            sendResponsePdu(((PduRequest) pdu).createResponse());
            if(pdu instanceof Unbind) {
                if (this.channel.isConnected())
                    this.state.set(STATE_UNBINDING);

                destroy();
            }
        } else if(pdu instanceof PduResponse) {
            PduResponse responsePdu = (PduResponse) pdu;
            WindowFuture<Integer,PduRequest,PduResponse> future;
            try {
                future = this.sendWindow.complete(responsePdu.getSequenceNumber(), responsePdu);
            } catch (InterruptedException e) {
                logger.error("Nio thread is interrupted while completing window, pduResponse is dropped " + responsePdu, e);
                return;
            }

            if(future != null) {
                this.countReceiveResponsePdu(responsePdu, future.getOfferToAcceptTime(), future.getAcceptToDoneTime(), (future.getAcceptToDoneTime() / future.getWindowSize()));

                if(!eventDispatcher.hasHandlers(PduResponseReceivedEvent.class))
                    return;

                eventDispatcher.dispatch(new PduResponseReceivedEvent(future.getRequest(), (PduResponse) pdu), this);

            } else {
                if(!eventDispatcher.hasHandlers(UnexpectedPduResponseReceivedEvent.class))
                    return;

                eventDispatcher.dispatch(new UnexpectedPduResponseReceivedEvent((PduResponse) pdu), this);
            }
        }
    }

    @Override
    public void fireExceptionThrown(Throwable t) {
        if(!eventDispatcher.hasHandlers(ExceptionThrownEvent.class))
            return;

        eventDispatcher.dispatch(new ExceptionThrownEvent(t), this);
    }

    @Override
    public void fireChannelClosed() {
        if(eventDispatcher.hasHandlers(ChannelClosedEvent.class)) {
            eventDispatcher.dispatch(new ChannelClosedEvent(), this);
        }

        destroy();
    }

    private void countSendRequestPdu(PduRequest pdu) {
        if (this.counters == null) {
            return;     // noop
        }
        
        if (pdu.isRequest()) {
            switch (pdu.getCommandId()) {
                case SmppConstants.CMD_ID_SUBMIT_SM:
                    this.counters.getTxSubmitSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_DELIVER_SM:
                    this.counters.getTxDeliverSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_DATA_SM:
                    this.counters.getTxDataSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_ENQUIRE_LINK:
                    this.counters.getTxEnquireLink().incrementRequestAndGet();
                    break;
            }
        }
    }
    
    private void countSendResponsePdu(PduResponse pdu, long responseTime, long estimatedProcessingTime) {
        if (this.counters == null) {
            return;     // noop
        }
        
        if (pdu.isResponse()) {
            switch (pdu.getCommandId()) {
                case SmppConstants.CMD_ID_SUBMIT_SM_RESP:
                    this.counters.getRxSubmitSM().incrementResponseAndGet();
                    this.counters.getRxSubmitSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getRxSubmitSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getRxSubmitSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_DELIVER_SM_RESP:
                    this.counters.getRxDeliverSM().incrementResponseAndGet();
                    this.counters.getRxDeliverSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getRxDeliverSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getRxDeliverSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_DATA_SM_RESP:
                    this.counters.getRxDataSM().incrementResponseAndGet();
                    this.counters.getRxDataSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getRxDataSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getRxDataSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
                    this.counters.getRxEnquireLink().incrementResponseAndGet();
                    this.counters.getRxEnquireLink().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getRxEnquireLink().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getRxEnquireLink().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
            }
        }
    }
    
    private void countSendRequestPduExpired(PduRequest pdu) {
        if (this.counters == null) {
            return;     // noop
        }
        
        if (pdu.isRequest()) {
            switch (pdu.getCommandId()) {
                case SmppConstants.CMD_ID_SUBMIT_SM:
                    this.counters.getTxSubmitSM().incrementRequestExpiredAndGet();
                    break;
                case SmppConstants.CMD_ID_DELIVER_SM:
                    this.counters.getTxDeliverSM().incrementRequestExpiredAndGet();
                    break;
                case SmppConstants.CMD_ID_DATA_SM:
                    this.counters.getTxDataSM().incrementRequestExpiredAndGet();
                    break;
                case SmppConstants.CMD_ID_ENQUIRE_LINK:
                    this.counters.getTxEnquireLink().incrementRequestExpiredAndGet();
                    break;
            }
        }
    }
    
    private void countReceiveRequestPdu(PduRequest pdu) {
        if (this.counters == null) {
            return;     // noop
        }
        
        if (pdu.isRequest()) {
            switch (pdu.getCommandId()) {
                case SmppConstants.CMD_ID_SUBMIT_SM:
                    this.counters.getRxSubmitSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_DELIVER_SM:
                    this.counters.getRxDeliverSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_DATA_SM:
                    this.counters.getRxDataSM().incrementRequestAndGet();
                    break;
                case SmppConstants.CMD_ID_ENQUIRE_LINK:
                    this.counters.getRxEnquireLink().incrementRequestAndGet();
                    break;
            }
        }
    }
    
    private void countReceiveResponsePdu(PduResponse pdu, long waitTime, long responseTime, long estimatedProcessingTime) {
        if (this.counters == null) {
            return;     // noop
        }
        
        if (pdu.isResponse()) {
            switch (pdu.getCommandId()) {
                case SmppConstants.CMD_ID_SUBMIT_SM_RESP:
                    this.counters.getTxSubmitSM().incrementResponseAndGet();
                    this.counters.getTxSubmitSM().addRequestWaitTimeAndGet(waitTime);
                    this.counters.getTxSubmitSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getTxSubmitSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getTxSubmitSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_DELIVER_SM_RESP:
                    this.counters.getTxDeliverSM().incrementResponseAndGet();
                    this.counters.getTxDeliverSM().addRequestWaitTimeAndGet(waitTime);
                    this.counters.getTxDeliverSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getTxDeliverSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getTxDeliverSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_DATA_SM_RESP:
                    this.counters.getTxDataSM().incrementResponseAndGet();
                    this.counters.getTxDataSM().addRequestWaitTimeAndGet(waitTime);
                    this.counters.getTxDataSM().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getTxDataSM().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getTxDataSM().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
                case SmppConstants.CMD_ID_ENQUIRE_LINK_RESP:
                    this.counters.getTxEnquireLink().incrementResponseAndGet();
                    this.counters.getTxEnquireLink().addRequestWaitTimeAndGet(waitTime);
                    this.counters.getTxEnquireLink().addRequestResponseTimeAndGet(responseTime);
                    this.counters.getTxEnquireLink().addRequestEstimatedProcessingTimeAndGet(estimatedProcessingTime);
                    this.counters.getTxEnquireLink().getResponseCommandStatusCounter().incrementAndGet(pdu.getCommandStatus());
                    break;
            }
        }
    }
    
    // mainly for JMX management
    public void resetCounters() {
        if (hasCounters()) {
            this.counters.reset();
        }
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

    public String getLocalTypeName() {
        return this.getLocalType().toString();
    }

    public String getRemoteTypeName() {
        return this.getRemoteType().toString();
    }

    public int getNextSequenceNumber() {
        return this.sequenceNumber.peek();
    }

    public String getLocalAddressAndPort() {
        if (this.channel != null) {
            InetSocketAddress addr = (InetSocketAddress)this.channel.getLocalAddress();
            return addr.getAddress().getHostAddress() + ":" + addr.getPort();
        } else {
            return null;
        }
    }

    public String getRemoteAddressAndPort() {
        if (this.channel != null) {
            InetSocketAddress addr = (InetSocketAddress)this.channel.getRemoteAddress();
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

    public int getMaxWindowSize() {
        return this.sendWindow.getMaxSize();
    }

    public int getWindowSize() {
        return this.sendWindow.getSize();
    }

    public long getWindowWaitTimeout() {
        return this.configuration.getWindowWaitTimeout();
    }
    
    public String[] dumpWindow() {
        Map<Integer,WindowFuture<Integer,PduRequest,PduResponse>> sortedSnapshot = this.sendWindow.createSortedSnapshot();
        String[] dump = new String[sortedSnapshot.size()];
        int i = 0;
        for (WindowFuture<Integer,PduRequest,PduResponse> future : sortedSnapshot.values()) {
            dump[i] = future.getRequest().toString();
            i++;
        }
        return dump;
    }

    public String getRxDataSMCounter() {
        return hasCounters() ? this.counters.getRxDataSM().toString() : null;
    }

    public String getRxDeliverSMCounter() {
        return hasCounters() ? this.counters.getRxDeliverSM().toString() : null;
    }

    public String getRxEnquireLinkCounter() {
        return hasCounters() ? this.counters.getRxEnquireLink().toString() : null;
    }

    public String getRxSubmitSMCounter() {
        return hasCounters() ? this.counters.getRxSubmitSM().toString() : null;
    }

    public String getTxDataSMCounter() {
        return hasCounters() ? this.counters.getTxDataSM().toString() : null;
    }

    public String getTxDeliverSMCounter() {
        return hasCounters() ? this.counters.getTxDeliverSM().toString() : null;
    }

    public String getTxEnquireLinkCounter() {
        return hasCounters() ? this.counters.getTxEnquireLink().toString() : null;
    }

    public String getTxSubmitSMCounter() {
        return hasCounters() ? this.counters.getTxSubmitSM().toString() : null;
    }
    
    public void enableLogBytes() {
        this.configuration.getLoggingOptions().setLogBytes(true);
    }
    
    public void disableLogBytes() {
        this.configuration.getLoggingOptions().setLogBytes(false);
    }
    
    public void enableLogPdu() {
        this.configuration.getLoggingOptions().setLogPdu(true);
    }
    
    public void disableLogPdu() {
        this.configuration.getLoggingOptions().setLogPdu(false);
    }
}
