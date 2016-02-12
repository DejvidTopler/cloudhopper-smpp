package com.cloudhopper.smpp.impl;

import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppSession;
import com.cloudhopper.smpp.async.events.BeforePduRequestSentEvent;
import com.cloudhopper.smpp.async.events.ChannelClosedEvent;
import com.cloudhopper.smpp.async.events.support.EventHandler;
import com.cloudhopper.smpp.async.events.SessionEvent;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.*;

/**
 * Created by ib-dtopler on 09.02.16..
 */
public class AsyncClientTest {
    private static final long REQ_EXPIRE_TIMEOUT = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncClientTest.class);

    private DefaultSmppServer server;
    private DefaultAsyncSmppClient client;
    private SmppSessionConfiguration sessionConfig;
    private PollableSmppSessionHandler serverSessionHandler;
    private HashSet<SmppServerSession> serverSessions;

    @Before
    public void before() throws SmppChannelException {
        DefaultSmppServerTest test = new DefaultSmppServerTest();
        server = test.createSmppServer();
        serverSessionHandler = test.serverHandler.sessionHandler;
        serverSessions = test.serverHandler.sessions;
        server.start();
        client = new DefaultAsyncSmppClient(Executors.newFixedThreadPool(4), 4);
        sessionConfig = test.createDefaultConfiguration();
        sessionConfig.setRequestExpiryTimeout(REQ_EXPIRE_TIMEOUT);
        sessionConfig.setWindowSize(10);
    }

    @After
    public void after() {
        server.stop();
    }

    @Test
    public void testBeforePduRequestSentEvent() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, new DefaultEventHandler<BeforePduRequestSentEvent>() {
            @Override
            public void handle(BeforePduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                count.incrementAndGet();
            }
        });

        DefaultAsyncSmppSession smppSession = bindSync();
        smppSession.sendRequestPdu(new SubmitSm(), new AwaitingPduSentCallback(1, 0, 0, 0));

        assertNotNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));
        assertEquals(2, count.get()); //bind and submit
    }

    @Test
    public void testBeforePduRequestSentEventOnlySubmit() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, new DefaultEventHandler<BeforePduRequestSentEvent>() {
            @Override
            public boolean canHandle(BeforePduRequestSentEvent sessionEvent) {
                return sessionEvent.getPduRequest() instanceof SubmitSm;
            }

            @Override
            public void handle(BeforePduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                count.incrementAndGet();
            }
        });

        DefaultAsyncSmppSession smppSession = bindSync();
        smppSession.sendRequestPdu(new SubmitSm(), new DefaultPduSentCallback());

        assertNotNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));
        assertEquals(1, count.get());
    }

    @Test
    public void testBeforePduRequestSentEventPreventExecution() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, new DefaultEventHandler<BeforePduRequestSentEvent>() {
            @Override
            public boolean canHandle(BeforePduRequestSentEvent sessionEvent) {
                return sessionEvent.getPduRequest() instanceof SubmitSm;
            }

            @Override
            public void handle(BeforePduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                count.incrementAndGet();
                sessionEvent.setStopExecution(true);
            }
        });

        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequestPdu(new SubmitSm(), callback);
        assertNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));
        assertEquals(1, count.get());
        callback.awaitAll();
    }

    @Test
    public void testCallbackSuccess() throws InterruptedException {
        serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
            @Override
            public PduResponse firePduRequestReceived(PduRequest pduRequest) {
                if (pduRequest instanceof SubmitSm)
                    return pduRequest.createResponse();
                return null;
            }
        });

        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(1, 0, 0, 0);
        smppSession.sendRequestPdu(new SubmitSm(), callback);
        callback.awaitAll();
    }

    @Test
    public void testCallbackExpire() throws InterruptedException {
        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 1, 0);
        smppSession.sendRequestPdu(new SubmitSm(), callback);

        Thread.sleep(REQ_EXPIRE_TIMEOUT * 2);
        smppSession.getSendWindow().cancelAllExpired();
        callback.awaitAll();
    }

    @Test
    public void testSubmitOnCloseSession() throws InterruptedException {
        DefaultAsyncSmppSession smppSession = bindSync();
        smppSession.destroy();
        server.stop();

        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 1, 0, 0);
        smppSession.sendRequestPdu(new SubmitSm(), callback);
        callback.awaitAll();
        assertTrue(callback.getException() instanceof ClosedChannelException);

        //todo check what is with event exception
    }

    @Test
    public void testUnbindExpired() throws InterruptedException {
        DefaultAsyncSmppSession smppSession = bindSync();
        assertNotNull(smppSession);

        assertTrue(smppSession.isBound());
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 1, 0);
        smppSession.unbind(callback);

        assertTrue(smppSession.isUnbinding());
        assertTrue(smppSession.getChannel().isConnected());
        assertTrue(smppSession.getChannel().isOpen());
        assertTrue(smppSession.getChannel().isBound());

        Thread.sleep(REQ_EXPIRE_TIMEOUT * 2);
        assertEquals(1, smppSession.getSendWindow().cancelAllExpired().size());

        callback.awaitAll();

        assertTrue(smppSession.isClosed());
        assertFalse(smppSession.getChannel().isConnected());
        assertFalse(smppSession.getChannel().isOpen());
        assertFalse(smppSession.getChannel().isBound());

        assertEquals(1, server.getChannelConnects());
        assertEquals(1, server.getChannelDisconnects());
        assertEquals(0, server.getConnectionSize());
    }

    @Test
    public void testUnbindOnClosedSession() throws InterruptedException {
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncSmppSession smppSession = bindSync();
        assertNotNull(smppSession);
        smppSession.destroy();
        sessionClosedWaiter.waitUntilTimeout();

        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 1, 0, 0);
        smppSession.unbind(callback);
        callback.awaitAll();
        assertTrue(callback.getException() instanceof ClosedChannelException);
        assertTrue(smppSession.isClosed());
    }

    @Test
    public void testUnexpectedCloseSession() throws InterruptedException {
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback sentCallback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequestPdu(new SubmitSm(), sentCallback);
        assertNotNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));

        server.stop();

        sentCallback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        assertTrue(smppSession.isClosed());
    }

    @Test
    public void testCloseSessionOnRequestedUnbind() throws InterruptedException {
        serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
            @Override
            public PduResponse firePduRequestReceived(PduRequest pduRequest) {
                if (pduRequest instanceof Unbind)
                    return pduRequest.createResponse();
                return null;
            }
        });

        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequestPdu(new SubmitSm(), callback);

        assertNotNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));

        AwaitingPduSentCallback unbindCallback = new AwaitingPduSentCallback(1, 0, 0, 0);
        smppSession.unbind(unbindCallback);
        assertTrue(smppSession.isUnbinding());

        unbindCallback.awaitAll();
        callback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        assertTrue(smppSession.isClosed());
    }

    private final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1);

    @Test
    public void testCloseSessionOnServerUnbind() throws InterruptedException {
        ServerUnbindResponseWaiter serverUnbindResponseWaiter = new ServerUnbindResponseWaiter();
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncSmppSession smppSession = bindSync();
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequestPdu(new SubmitSm(), callback);

        assertNotNull(serverSessionHandler.getReceivedPduRequests().poll(2, TimeUnit.SECONDS));

        serverUnbindResponseWaiter.waitOrTimeout();
        callback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        assertTrue(smppSession.isClosed());
    }


    private class ClientSessionClosedWaiter {
        private final CountDownLatch chCloseEventInvoked;

        public ClientSessionClosedWaiter() {
            chCloseEventInvoked = new CountDownLatch(1);
            client.getEventDispatcher().addHandler(ChannelClosedEvent.class, new DefaultEventHandler<ChannelClosedEvent>() {
                @Override
                public void handle(ChannelClosedEvent sessionEvent, AsyncSmppSession session) {
                    chCloseEventInvoked.countDown();
                }
            });
        }

        public void waitUntilTimeout() throws InterruptedException {
            chCloseEventInvoked.await(2, TimeUnit.SECONDS);
        }
    }

    private class ServerUnbindResponseWaiter {
        private final CountDownLatch unbindRespWaiter;
        private final AtomicReference<Throwable> err;

        public ServerUnbindResponseWaiter() {
            unbindRespWaiter = new CountDownLatch(1);
            err = new AtomicReference<>();
            serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
                @Override
                public PduResponse firePduRequestReceived(PduRequest pduRequest) {
                    if (pduRequest instanceof SubmitSm) {
                        scheduled.schedule(() -> {
                            try {
                                WindowFuture<Integer, PduRequest, PduResponse> window = serverSessions.iterator().next().sendRequestPdu(new Unbind(), 0, true);
                                assertTrue(window.await());
                            } catch (Throwable t) {
                                err.set(t);
                            }
                            unbindRespWaiter.countDown();
                        }, 500, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
            });
        }

        public void waitOrTimeout() throws InterruptedException {
            unbindRespWaiter.await(2, TimeUnit.SECONDS);
            assertNotNull(err.get().getMessage(), err.get());
        }
    }

    private DefaultAsyncSmppSession bindSync() throws InterruptedException {
        CountDownLatch wait = new CountDownLatch(1);
        AtomicReference<DefaultAsyncSmppSession> ref = new AtomicReference<>();
        client.bindAsync(sessionConfig, new BindCallback() {
            @Override
            public void onBindSucess(DefaultAsyncSmppSession smppSession) {
                ref.set(smppSession);
                wait.countDown();
            }

            @Override
            public void onFailure(Reason reason, Throwable t, BaseBindResp response) {
                wait.countDown();
            }
        });

        assertTrue(wait.await(200, TimeUnit.SECONDS));
        assertNotNull(ref.get());
        return ref.get();
    }

    public class DefaultEventHandler<R extends SessionEvent> implements EventHandler<R> {
        @Override
        public boolean canHandle(R sessionEvent) {
            return true;
        }

        @Override
        public void handle(R sessionEvent, AsyncSmppSession session) {
        }
    }

    public class AwaitingPduSentCallback extends DefaultPduSentCallback {
        private final static int AWAIT_SECONDS = 2;
        private final CountDownLatch successWait;
        private final CountDownLatch failureWait;
        private final CountDownLatch expireWait;
        private final CountDownLatch cancelWait;
        private final AtomicReference<Throwable> exception;

        public AwaitingPduSentCallback(int expectedSuccess, int expectedFailure, int expectedExpire,
                int expectedCancel) {
            successWait = new CountDownLatch(expectedSuccess);
            failureWait = new CountDownLatch(expectedFailure);
            expireWait = new CountDownLatch(expectedExpire);
            cancelWait = new CountDownLatch(expectedCancel);
            exception = new AtomicReference<>();
        }

        @Override
        public void onSuccess(PduResponse response) {
            super.onSuccess(response);
            successWait.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            super.onFailure(t);
            failureWait.countDown();
            this.exception.set(t);
        }

        @Override
        public void onExpire() {
            super.onExpire();
            expireWait.countDown();
        }

        @Override
        public void onCancel() {
            super.onCancel();
            cancelWait.countDown();
        }

        public void awaitAll() throws InterruptedException {
            awaitSucess();
            awaitFailure();
            awaitCancel();
            awaitExpire();
        }

        public void awaitSucess() throws InterruptedException {
            assertTrue(successWait.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        }

        public void awaitFailure() throws InterruptedException {
            assertTrue(failureWait.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        }


        public void awaitCancel() throws InterruptedException {
            assertTrue(cancelWait.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        }

        public void awaitExpire() throws InterruptedException {
            assertTrue(expireWait.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        }

        public Throwable getException() {
            return exception.get();
        }
    }

    public class DefaultPduSentCallback implements PduSentCallback {

        @Override
        public void onSuccess(PduResponse response) {
            LOGGER.info("OnSucecss " + response);
        }

        @Override
        public void onFailure(Throwable t) {
            LOGGER.info("onFailure " + t, t);
        }

        @Override
        public void onExpire() {
            LOGGER.info("onExpire");
        }

        @Override
        public void onCancel() {
            LOGGER.info("onCancel");
        }
    }
}
