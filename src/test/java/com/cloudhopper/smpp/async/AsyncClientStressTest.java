package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.server.DefaultAsyncSmppServer;
import com.cloudhopper.smpp.async.session.DefaultAsyncClientSmppSession;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Created by ib-dtopler on 09.02.16..
 */
@Ignore
public class AsyncClientStressTest {
    private static final long REQ_EXPIRE_TIMEOUT = 500;
    private static final int THREAD_COUNT = 10;

    private DefaultAsyncSmppServer server;
    private DefaultAsyncSmppClient client;
    private SmppSessionConfiguration sessionConfig;
    private ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    @Before
    public void before() throws SmppChannelException {
        server = AsyncClientTestUtils.createSmppServer();
        server.start();
        client = AsyncClientTestUtils.createSmppClient();
        sessionConfig = AsyncClientTestUtils.createDefaultConfiguration();
        sessionConfig.setRequestExpiryTimeout(REQ_EXPIRE_TIMEOUT);
        sessionConfig.setWindowSize(Integer.MAX_VALUE);
    }

    @After
    public void after() {
        server.stop();
    }

    @Test
    public void stress() throws InterruptedException {
        AtomicInteger serverSubmitSmCounter = AsyncClientTestUtils.addServerSubmitSmCounter(server, false);
        AtomicInteger serverSubmitSmRespCounter = AsyncClientTestUtils.addServerSubmitSmRespCounter(server);
        AtomicInteger clientSubmitSmCounter = AsyncClientTestUtils.addClientSubmitSmCounter(client, false);
        AtomicInteger clientSubmitSmRespCounter = AsyncClientTestUtils.addClientSubmitSmRespCounter(client);


        int SUBMIT_PER_THREAD = 50;
        int SUBMITION_COUNT = 50;
        DefaultAsyncClientSmppSession session = AsyncClientTestUtils.bindSync(client, sessionConfig);

        int sentCount = SUBMIT_PER_THREAD * SUBMITION_COUNT;
        AwaitingPduSentCallback callback = new AwaitingPduSentCallback(sentCount, 0, 0, 0);
        callback.setTimeoutInSeconds(20);

        for (int i = 0; i < SUBMITION_COUNT; i++)
            sendMsgs(SUBMIT_PER_THREAD, session, callback);

        callback.awaitAll();
        assertEquals(sentCount, serverSubmitSmCounter.get());
        assertEquals(sentCount, serverSubmitSmRespCounter.get());

        assertEquals(sentCount, clientSubmitSmRespCounter.get());
        assertEquals(sentCount, clientSubmitSmCounter.get());
    }

    private void sendMsgs(int SUBMIT_PER_THREAD, DefaultAsyncClientSmppSession session,
            AwaitingPduSentCallback callback) {
        executorService.execute(() -> {
            for (int i = 0; i < SUBMIT_PER_THREAD; i++)
                session.sendRequest(new AsyncRequestContext(new SubmitSm(), session, callback));
        });
    }
}
