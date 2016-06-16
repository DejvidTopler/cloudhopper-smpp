package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.DefaultPduSentCallback;
import com.cloudhopper.smpp.async.callback.PduSentCallback;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.events.ChannelClosedEvent;
import com.cloudhopper.smpp.async.events.ExceptionThrownEvent;
import com.cloudhopper.smpp.async.events.PduRequestReceivedEvent;
import com.cloudhopper.smpp.async.events.handler.DefaultEventHandler;
import com.cloudhopper.smpp.async.events.handler.EventHandler;
import com.cloudhopper.smpp.async.server.DefaultAsyncSmppServer;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;
import com.cloudhopper.smpp.async.session.DefaultAsyncClientSmppSession;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.BindReceiver;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.junit.*;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

/**
 * Created by ib-dtopler on 09.02.16..
 */
public class AsyncClientTest {
    private static final long REQ_EXPIRE_TIMEOUT = 500;

    private DefaultAsyncSmppServer server;
    private DefaultAsyncSmppClient client;
    private SmppSessionConfiguration sessionConfig;

    @Before
    public void before() throws SmppChannelException {
        server = AsyncClientTestUtils.createSmppServer();
        server.start();
        client = AsyncClientTestUtils.createSmppClient();
        sessionConfig = AsyncClientTestUtils.createDefaultConfiguration();
        sessionConfig.setRequestExpiryTimeout(REQ_EXPIRE_TIMEOUT);
        sessionConfig.setWindowSize(10);
    }

    @After
    public void after() {
        server.destroy();
        client.destroy();
    }

    @Test
    public void testConnectionTimeout() throws InterruptedException {
        sessionConfig.setHost("www.google.com");
        sessionConfig.setPort(81);
        sessionConfig.setConnectTimeout(150);

        AsyncBindClientAwaiter asyncBindClientAwaiter = new AsyncBindClientAwaiter();
        asyncBindClientAwaiter.bind(client, sessionConfig);
        asyncBindClientAwaiter.awaitForReason(BindCallback.Reason.CONNECT_TIMEOUT);
    }

    @Test
    @Ignore
    public void testBufferOverflow() throws InterruptedException {
        ExceptionThrownEventChecker checker = new ExceptionThrownEventChecker();
//        serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
//            @Override
//            public PduResponse firePduRequestReceived(PduRequest pduRequest) {
//                if (pduRequest instanceof SubmitSm){
//                    try {
//                        Thread.sleep(Long.MAX_VALUE);
//                    } catch (InterruptedException e) {
//                    }
//                }
//                return null;
//            }
//        });


        sessionConfig.setRequestExpiryTimeout(20_000);
        sessionConfig.setWindowSize(10_000);
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);

        for (int i = 0; i < 10_000; i++) {
            smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, new DefaultPduSentCallback()));
        }

        Thread.sleep(10_000);
        System.out.println(checker.getError());

    }

    @Test
    public void testBeforePduRequestSentEvent() throws InterruptedException {
        AtomicInteger clientPduReqSentCount = AsyncClientTestUtils.addClientReqCounter(client);
        AtomicInteger serverPduReqRecCount = AsyncClientTestUtils.addServerReqCounter(server);

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, new AwaitingPduSentCallback(1, 0, 0, 0)));

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverPduReqRecCount.get() == 2);
        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> clientPduReqSentCount.get() == 2);
    }

    @Test
    public void testBeforePduRequestSentEventOnlySubmit() throws InterruptedException {
        AtomicInteger serverPduReqRecCount = AsyncClientTestUtils.addServerReqCounter(server);
        AtomicInteger clientSubmitSmCount = AsyncClientTestUtils.addClientSubmitSmCounter(client, false);

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, new DefaultPduSentCallback()));

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverPduReqRecCount.get() == 2);
        assertEquals(1, clientSubmitSmCount.get());
    }

    @Test
    public void testBeforePduRequestSentEventPreventExecution() throws InterruptedException {
        AtomicInteger serverPduReqRecCount = AsyncClientTestUtils.addServerReqCounter(server);
        AtomicInteger clientSubmitSmCount = AsyncClientTestUtils.addClientSubmitSmCounter(client, true);

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));
        assertEquals(1, serverPduReqRecCount.get());
        assertEquals(1, clientSubmitSmCount.get());
        callback.awaitAll();
    }

    @Test
    public void cancelDuplicateBind() throws InterruptedException {
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);

        CountDownLatch wait = new CountDownLatch(1);
        AtomicReference<BindCallback.Reason> reasonRef = new AtomicReference<>();

        smppSession.bind(new BindReceiver(), new BindCallback() {
            @Override
            public void onBindSucess(DefaultAsyncClientSmppSession smppSession) {
            }

            @Override
            public void onFailure(Reason reason, Throwable t, BaseBindResp response) {
                reasonRef.set(reason);
                wait.countDown();
            }
        });

        wait.await(2, TimeUnit.SECONDS);
        assertEquals(BindCallback.Reason.INVALID_SESSION_STATE, reasonRef.get());
    }

    @Test
    public void testCallbackSuccess() throws InterruptedException {
//        serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
//            @Override
//            public PduResponse firePduRequestReceived(PduRequest pduRequest) {
//                if (pduRequest instanceof SubmitSm)
//                    return pduRequest.createResponse();
//                return null;
//            }
//        });

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(1, 0, 0, 0);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));
        callback.awaitAll();
    }

    @Test
    public void testCallbackExpire() throws InterruptedException {
        AsyncClientTestUtils.blockServerRespExceptBind(server);
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 1, 0);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));

        Thread.sleep(REQ_EXPIRE_TIMEOUT * 2);
        smppSession.getSendWindow().cancelAllExpired();
        callback.awaitAll();
    }

    @Test
    public void testCallbackCancelOnDestroy() throws InterruptedException {
        AtomicInteger serverSubmitRecCounter = AsyncClientTestUtils.addServerSubmitSmCounter(server, true);
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverSubmitRecCounter.get() == 1);

        client.destroy();
        callback.awaitAll();
    }

    @Test
    public void testSubmitOnCloseSession() throws InterruptedException {
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        smppSession.destroy();
        server.stop();
        sessionClosedWaiter.waitUntilTimeout();

        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));
        callback.awaitAll();
        callback.assertCancelReason(PduSentCallback.CancelReason.INVALID_SESSION_STATE);
    }

    @Test
    public void testUnbindExpired() throws InterruptedException {
        AsyncClientTestUtils.blockServerRespExceptBind(server);
        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        Assert.assertNotNull(smppSession);

        Assert.assertTrue(smppSession.isBound());
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 1, 0);
        smppSession.unbind(callback);

        Assert.assertTrue(smppSession.isUnbinding());
        Assert.assertTrue(smppSession.getChannel().isConnected());
        Assert.assertTrue(smppSession.getChannel().isOpen());
        Assert.assertTrue(smppSession.getChannel().isBound());

        Thread.sleep(REQ_EXPIRE_TIMEOUT * 2);
        assertEquals(1, smppSession.getSendWindow().cancelAllExpired().size());

        callback.awaitAll();

        Assert.assertTrue(smppSession.isClosed());
        Assert.assertFalse(smppSession.getChannel().isConnected());
        Assert.assertFalse(smppSession.getChannel().isOpen());
        Assert.assertFalse(smppSession.getChannel().isBound());

        assertEquals(1, server.getCounters().getChannelConnects());
        assertEquals(1, server.getCounters().getChannelDisconnects());
        assertEquals(0, server.getConnectionSize());
    }

    @Test
    public void testUnbindOnClosedSession() throws InterruptedException {
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        Assert.assertNotNull(smppSession);
        smppSession.destroy();
        sessionClosedWaiter.waitUntilTimeout();

        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 1, 0, 0);
        smppSession.unbind(callback);
        callback.awaitAll();
        Assert.assertTrue(callback.getException() instanceof ClosedChannelException);
        Assert.assertTrue(smppSession.isClosed());
    }

    @Test
    public void testUnexpectedCloseSession() throws InterruptedException {
        AtomicInteger serverSubmitCount = AsyncClientTestUtils.addServerSubmitSmCounter(server, true);
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback sentCallback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, sentCallback));
        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverSubmitCount.get() == 1);

        server.stop();

        sentCallback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        Assert.assertTrue(smppSession.isClosed());
    }

    @Test
    public void testCloseSessionOnRequestedUnbind() throws InterruptedException {
        AtomicInteger serverPduReqRecCount = AsyncClientTestUtils.addServerSubmitSmCounter(server, true);
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback submitCallback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, submitCallback));

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverPduReqRecCount.get() == 1);

        AwaitingPduSentCallback unbindCallback = new AwaitingPduSentCallback(1, 0, 0, 0);
        smppSession.unbind(unbindCallback);
        Assert.assertTrue(smppSession.isUnbinding());

        unbindCallback.awaitAll();
        submitCallback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        Assert.assertTrue(smppSession.isClosed());
    }

    private final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1);

    @Test
    public void testCloseSessionOnServerUnbind() throws InterruptedException {
        AtomicInteger serverSubmitSmCounter = AsyncClientTestUtils.addServerSubmitSmCounter(server, true);
        ClientSessionClosedWaiter sessionClosedWaiter = new ClientSessionClosedWaiter();

        DefaultAsyncClientSmppSession smppSession = AsyncClientTestUtils.bindSync(client, sessionConfig);
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(0, 0, 0, 1);
        smppSession.sendRequest(new AsyncRequestContext(new SubmitSm(), smppSession, callback));

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> serverSubmitSmCounter.get() == 1);

        AwaitingPduSentCallback unbindCallback = new AwaitingPduSentCallback(1, 0, 0, 0);
        server.getSessions().values().iterator().next().unbind(unbindCallback);
        callback.awaitAll();
        sessionClosedWaiter.waitUntilTimeout();
        Assert.assertTrue(smppSession.isClosed());
    }

    private class ExceptionThrownEventChecker {
        private final AtomicReference<Throwable> err = new AtomicReference<>();

        public ExceptionThrownEventChecker() {
            client.getEventDispatcher().addHandler(ExceptionThrownEvent.class, new DefaultEventHandler<ExceptionThrownEvent>() {
                @Override
                public void handle(ExceptionThrownEvent sessionEvent, AsyncSmppSession session) {
                    err.set(sessionEvent.getCause());
                }
            });
        }

        public Throwable getError() {
            Assert.assertNotNull(err.get());
            return err.get();
        }
    }

    private class ClientSessionClosedWaiter {
        private final CountDownLatch chCloseEventInvoked;

        public ClientSessionClosedWaiter() {
            chCloseEventInvoked = new CountDownLatch(1);
            client.getEventDispatcher().addHandler(ChannelClosedEvent.class, (DefaultEventHandler<ChannelClosedEvent>) (sessionEvent, session) -> chCloseEventInvoked.countDown());
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
            server.getEventDispatcher().addHandler(PduRequestReceivedEvent.class, new EventHandler<PduRequestReceivedEvent>() {
                @Override
                public boolean canHandle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                    return sessionEvent.getPduRequest() instanceof SubmitSm;
                }

                @Override
                public void handle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                    scheduled.schedule(() -> {
                        try {
                            AwaitingPduSentCallback callback = new AwaitingPduSentCallback(1, 0, 0, 0);
                            session.unbind(callback);
                            callback.awaitAll();
                        } catch (Throwable t) {
                            err.set(t);
                        }
                        unbindRespWaiter.countDown();
                    }, 500, TimeUnit.MILLISECONDS);
                }
            });
        }

        public void waitOrTimeout() throws InterruptedException {
            unbindRespWaiter.await(2, TimeUnit.SECONDS);
            Assert.assertNotNull(err.get().getMessage(), err.get());
        }
    }

}
