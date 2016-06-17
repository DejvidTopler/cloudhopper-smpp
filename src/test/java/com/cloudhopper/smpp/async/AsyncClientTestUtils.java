package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.events.*;
import com.cloudhopper.smpp.async.events.handler.DefaultEventHandler;
import com.cloudhopper.smpp.async.events.support.EventDispatcher;
import com.cloudhopper.smpp.async.events.support.EventDispatcher.ExecutionOrder;
import com.cloudhopper.smpp.async.events.support.EventDispatcherImpl;
import com.cloudhopper.smpp.async.server.DefaultAsyncSmppServer;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;
import com.cloudhopper.smpp.async.session.DefaultAsyncClientSmppSession;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class AsyncClientTestUtils {

    public static final Logger LOGGER = LoggerFactory.getLogger("CommLogs");

    private static final int PORT = 9097;
    private static final String SYSTEMID = "sysId";
    private static final String PASSWORD = "pass";

    public static DefaultAsyncClientSmppSession bindSync(DefaultAsyncSmppClient client,
            SmppSessionConfiguration sessionConfig) throws InterruptedException {

        AsyncBindClientAwaiter awaiter = new AsyncBindClientAwaiter();
        awaiter.bind(client, sessionConfig);
        return awaiter.awaitForSessionBound();
    }

    public static DefaultAsyncSmppClient createSmppClient() {
        DefaultAsyncSmppClient client = new DefaultAsyncSmppClient(Executors.newFixedThreadPool(4), 4, Executors.newFixedThreadPool(1));
        registerLoggingHandler(client.getEventDispatcher());
        return client;
    }

    private static void registerLoggingHandler(EventDispatcher eventDispatcher) {
        eventDispatcher.addHandler(PduRequestReceivedEvent.class, new DefaultEventHandler<PduRequestReceivedEvent>() {
            @Override
            public void handle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                LOGGER.debug("Received pduReq - Session: " + session + ", pdu: " + sessionEvent.getPduRequest().toString());
            }
        }, ExecutionOrder.BEFORE);

        eventDispatcher.addHandler(PduResponseReceivedEvent.class, new DefaultEventHandler<PduResponseReceivedEvent>() {
            @Override
            public void handle(PduResponseReceivedEvent sessionEvent, AsyncSmppSession session) {
                LOGGER.debug("Received pduRes - Session: " + session + ", pdu: " + sessionEvent.getPduResponse().toString());
            }
        }, ExecutionOrder.BEFORE);

        eventDispatcher.addHandler(PduRequestSentEvent.class, new DefaultEventHandler<PduRequestSentEvent>() {
            @Override
            public void handle(PduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                LOGGER.debug("Sent pduReq - Session: " + session + ", pdu: " + sessionEvent.getCtx().getRequest().toString());
            }
        }, ExecutionOrder.BEFORE);

        eventDispatcher.addHandler(PduResponseSentEvent.class, new DefaultEventHandler<PduResponseSentEvent>() {
            @Override
            public void handle(PduResponseSentEvent sessionEvent, AsyncSmppSession session) {
                LOGGER.debug("Sent pduRes - Session: " + session + ", pdu: " + sessionEvent.getPduResponse().toString());
            }
        }, ExecutionOrder.BEFORE);

    }

    public static DefaultAsyncSmppServer createSmppServer() {
        SmppServerConfiguration configuration = createSmppServerConfiguration();
        int threadCount = 4;
        DefaultAsyncSmppServer smppServer = new DefaultAsyncSmppServer(configuration, Executors.newFixedThreadPool(1),
                threadCount, Executors.newFixedThreadPool(threadCount), new EventDispatcherImpl());

        registerLoggingHandler(smppServer.getEventDispatcher());
        return smppServer;
    }

    public static SmppServerConfiguration createSmppServerConfiguration() {
        SmppServerConfiguration configuration = new SmppServerConfiguration();
        configuration.setPort(PORT);
        configuration.setSystemId("cloudhopper");
        return configuration;
    }

    public static SmppSessionConfiguration createDefaultConfiguration() {
        SmppSessionConfiguration configuration = new SmppSessionConfiguration();
        configuration.setWindowSize(1);
        configuration.setName("Tester.Session.0");
        configuration.setType(SmppBindType.TRANSCEIVER);
        configuration.setHost("localhost");
        configuration.setPort(PORT);
        configuration.setConnectTimeout(100);
        configuration.setBindTimeout(100);
        configuration.setSystemId(SYSTEMID);
        configuration.setPassword(PASSWORD);
        configuration.getLoggingOptions().setLogBytes(true);
        return configuration;
    }

    public static AtomicInteger addServerReqCounter(DefaultAsyncSmppServer server) {
        AtomicInteger serverPduReqRecCount = new AtomicInteger();
        server.getEventDispatcher().addHandler(PduRequestReceivedEvent.class, (DefaultEventHandler<PduRequestReceivedEvent>) (sessionEvent, session) -> serverPduReqRecCount.incrementAndGet());
        return serverPduReqRecCount;
    }

    public static AtomicInteger addClientReqCounter(DefaultAsyncSmppClient client) {
        AtomicInteger clientPduReqSentCount = new AtomicInteger();
        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, (DefaultEventHandler<BeforePduRequestSentEvent>) (sessionEvent, session) -> clientPduReqSentCount.incrementAndGet());
        return clientPduReqSentCount;
    }

    public static AtomicInteger addClientSubmitSmCounter(DefaultAsyncSmppClient client, boolean stopExecution) {
        AtomicInteger count = new AtomicInteger();
        client.getEventDispatcher().addHandler(BeforePduRequestSentEvent.class, new DefaultEventHandler<BeforePduRequestSentEvent<SubmitSm, SubmitSmResp>>() {
            @Override
            public boolean canHandle(BeforePduRequestSentEvent sessionEvent, AsyncSmppSession session) {
                return sessionEvent.getCtx().getRequest() instanceof SubmitSm;
            }

            @Override
            public void handle(BeforePduRequestSentEvent<SubmitSm, SubmitSmResp> sessionEvent,
                    AsyncSmppSession session) {
                count.incrementAndGet();
                sessionEvent.setStopExecution(stopExecution);
            }
        });

        return count;
    }

    public static AtomicInteger addServerSubmitSmCounter(DefaultAsyncSmppServer server, boolean stopExecution) {
        AtomicInteger count = new AtomicInteger();
        server.getEventDispatcher().addHandler(PduRequestReceivedEvent.class, new DefaultEventHandler<PduRequestReceivedEvent>() {
            @Override
            public boolean canHandle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                return sessionEvent.getPduRequest() instanceof SubmitSm;
            }

            @Override
            public void handle(PduRequestReceivedEvent sessionEvent,
                    AsyncSmppSession session) {
                count.incrementAndGet();
                sessionEvent.setStopExecution(stopExecution);
            }
        });

        return count;
    }

    public static void blockServerRespExceptBind(DefaultAsyncSmppServer server) {
        server.getEventDispatcher().addHandler(PduRequestReceivedEvent.class, new DefaultEventHandler<PduRequestReceivedEvent>() {
            @Override
            public boolean canHandle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                return !(sessionEvent.getPduRequest() instanceof BaseBind);
            }

            @Override
            public void handle(PduRequestReceivedEvent sessionEvent, AsyncSmppSession session) {
                sessionEvent.setStopExecution(true);
            }
        });
    }

    public static AtomicInteger addClientSubmitSmRespCounter(DefaultAsyncSmppClient client) {
        AtomicInteger counter = new AtomicInteger();
        client.getEventDispatcher().addHandler(PduResponseReceivedEvent.class, new DefaultEventHandler<PduResponseReceivedEvent>() {
            @Override
            public boolean canHandle(PduResponseReceivedEvent sessionEvent, AsyncSmppSession session) {
                return sessionEvent.getPduResponse() instanceof SubmitSmResp;
            }

            @Override
            public void handle(PduResponseReceivedEvent sessionEvent, AsyncSmppSession session) {
                counter.incrementAndGet();
            }
        });
        return counter;
    }

    public static AtomicInteger addServerSubmitSmRespCounter(DefaultAsyncSmppServer server) {
        AtomicInteger counter = new AtomicInteger();
        server.getEventDispatcher().addHandler(BeforePduResponseSentEvent.class, new DefaultEventHandler<BeforePduResponseSentEvent>() {
            @Override
            public boolean canHandle(BeforePduResponseSentEvent sessionEvent, AsyncSmppSession session) {
                return sessionEvent.getPduResponse() instanceof SubmitSmResp;
            }

            @Override
            public void handle(BeforePduResponseSentEvent sessionEvent, AsyncSmppSession session) {
                counter.incrementAndGet();
            }
        });
        return counter;
    }
}
