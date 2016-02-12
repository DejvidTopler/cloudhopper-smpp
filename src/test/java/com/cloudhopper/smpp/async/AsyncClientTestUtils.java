package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppSession;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class AsyncClientTestUtils {

    public static DefaultAsyncSmppSession bindSync(DefaultAsyncSmppClient client, SmppSessionConfiguration sessionConfig) throws InterruptedException {
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

        Assert.assertTrue(wait.await(200, TimeUnit.SECONDS));
        Assert.assertNotNull(ref.get());
        return ref.get();
    }

}
