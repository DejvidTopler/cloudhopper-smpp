package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.callback.BindCallback.Reason;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.session.DefaultAsyncClientSmppSession;
import com.cloudhopper.smpp.pdu.BaseBindResp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by ib-dtopler on 16.02.16..
 */
public class AsyncBindClientAwaiter {
    private final CountDownLatch wait = new CountDownLatch(1);
    private final AtomicReference<DefaultAsyncClientSmppSession> ref = new AtomicReference<>();
    private final AtomicReference<Reason> reasonRef = new AtomicReference<>();

    public void bind(DefaultAsyncSmppClient client, SmppSessionConfiguration sessionConfig) {
        client.bind(sessionConfig, new BindCallback() {
            @Override
            public void onBindSucess(DefaultAsyncClientSmppSession smppSession) {
                ref.set(smppSession);
                wait.countDown();
            }

            @Override
            public void onFailure(Reason reason, Throwable t, BaseBindResp response) {
                reasonRef.set(reason);
                wait.countDown();
            }
        });
    }

    public DefaultAsyncClientSmppSession awaitForSessionBound() throws InterruptedException {
        assertTrue(wait.await(2000, TimeUnit.SECONDS));
        return ref.get();
    }

    public void awaitForReason(Reason expectedReason) throws InterruptedException {
        assertTrue(wait.await(300, TimeUnit.MILLISECONDS));
        assertEquals(expectedReason, reasonRef.get());
    }
}
