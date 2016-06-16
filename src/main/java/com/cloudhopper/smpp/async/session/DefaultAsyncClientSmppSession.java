package com.cloudhopper.smpp.async.session;


import com.cloudhopper.smpp.AsyncClientSmppSession;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.AsyncRequestContext;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.timeout.ReadTimeoutException;

public class DefaultAsyncClientSmppSession extends AbstractAsyncSmppSession implements AsyncClientSmppSession {

    public DefaultAsyncClientSmppSession(SmppSessionConfiguration configuration, Channel channel,
            EventDispatcher eventDispatcher) {
        super(configuration, channel, eventDispatcher);
    }

    @Override
    protected boolean isSessionReadyForSubmit(Pdu pdu, State state) {
        if (pdu instanceof BaseBind) {
            return !State.CLOSED.equals(state);
        }

        if (pdu instanceof Unbind) {
            return !State.CLOSED.equals(state);
        }

        return State.BOUND.equals(state);
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
                    DefaultAsyncClientSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.NEGATIVE_BIND_RESP, null, bindResp));
                else {
                    setBound();
                    bindCallback.onBindSucess(DefaultAsyncClientSmppSession.this);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                DefaultAsyncClientSmppSession.this.close(future -> {
                    if (t instanceof RecoverablePduException || t instanceof UnrecoverablePduException || t instanceof SmppTimeoutException ||
                            t instanceof SmppChannelException || t instanceof InterruptedException)
                        bindCallback.onFailure(BindCallback.Reason.SEND_BIND_REQ_FAILED, t, null);
                    else
                        bindCallback.onFailure(BindCallback.Reason.READ_ERROR, t, null);
                });

            }

            @Override
            public void onExpire() {
                DefaultAsyncClientSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.READ_TIMEOUT, new ReadTimeoutException("Request expire in window"), null));
            }

            @Override
            public void onCancel(CancelReason cancelReason) {
                DefaultAsyncClientSmppSession.this.close(future -> bindCallback.onFailure(BindCallback.Reason.CONNECT_CANCELED, null, null));
            }
        });

        ctx.setWindowTimeout(configuration.getBindTimeout());
        sendRequest(ctx);
    }


}
