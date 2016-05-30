package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.AsyncSmppSession;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.events.BeforePduRequestSentEvent;
import com.cloudhopper.smpp.async.events.PduResponseReceivedEvent;
import com.cloudhopper.smpp.async.events.handler.DefaultEventHandler;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppServerTest;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.impl.PollableSmppSessionHandler;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ib-dtopler on 09.02.16..
 */
@Ignore
public class ManualTest {
    private static final long REQ_EXPIRE_TIMEOUT = 500;
    private static final int THREAD_COUNT = 10;

    private DefaultSmppServer server;
    private DefaultAsyncSmppClient client;
    private SmppSessionConfiguration sessionConfig;
    private PollableSmppSessionHandler serverSessionHandler;
    private HashSet<SmppServerSession> serverSessions;
    private ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    @Before
    public void before() throws SmppChannelException {
        DefaultSmppServerTest test = new DefaultSmppServerTest();
        server = test.createSmppServer();
        serverSessionHandler = test.serverHandler.sessionHandler;
        serverSessions = test.serverHandler.sessions;
        server.start();
        client = new DefaultAsyncSmppClient(Executors.newFixedThreadPool(4), Executors.newFixedThreadPool(1), 4);
        sessionConfig = test.createDefaultConfiguration();
        sessionConfig.setRequestExpiryTimeout(REQ_EXPIRE_TIMEOUT);
        sessionConfig.setWindowSize(Integer.MAX_VALUE);

        serverSessionHandler.addListener(new DefaultSmppSessionHandler() {
            @Override
            public PduResponse firePduRequestReceived(PduRequest pduRequest) {
                if (pduRequest instanceof SubmitSm)
                    return pduRequest.createResponse();
                return null;
            }
        });
    }

    @After
    public void after() {
        server.stop();
    }

    @Test
    public void stress() throws InterruptedException {
        AtomicInteger pduRequestCount = new AtomicInteger();
        AtomicInteger pduResponseCount = new AtomicInteger();

        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, new DefaultEventHandler<BeforePduRequestSentEvent>() {
            @Override
            public void handle(BeforePduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                pduRequestCount.incrementAndGet();
            }
        });

        client.getEventDispatcher().addHandler(PduResponseReceivedEvent.class, new DefaultEventHandler<PduResponseReceivedEvent>() {
            @Override
            public void handle(PduResponseReceivedEvent sessionEvent, AsyncSmppSession session) {
                pduResponseCount.incrementAndGet();
            }
        });


        Thread.sleep(Long.MAX_VALUE);
    }

}
