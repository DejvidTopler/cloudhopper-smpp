package com.cloudhopper.smpp.impl;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.type.SmppChannelException;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.*;

/**
 * Created by ib-dtopler on 09.02.16..
 */
public class AsyncClientTest {
    private static final long REQ_EXPIRE_TIMEOUT = 500;
    private DefaultSmppServer server;
    private DefaultSmppClient client;
    private SmppSessionConfiguration sessionConfig;

    @Before
    public void before() throws SmppChannelException {
        DefaultSmppServerTest test = new DefaultSmppServerTest();
        server = test.createSmppServer();
        server.start();
        client = new DefaultSmppClient();
        sessionConfig = test.createDefaultConfiguration();
        sessionConfig.setRequestExpiryTimeout(REQ_EXPIRE_TIMEOUT);
    }


    @Test
    public void testBindUnbind() throws InterruptedException {
        DefaultSmppSession smppSession = bindSync();
        assertNotNull(smppSession);

        assertTrue(smppSession.isBound());
        CountDownLatch wait = new CountDownLatch(1);
        smppSession.unbindAsync(() -> wait.countDown());

        assertTrue(smppSession.isUnbinding());
        assertTrue(smppSession.getChannel().isConnected());
        assertTrue(smppSession.getChannel().isOpen());
        assertTrue(smppSession.getChannel().isBound());

        Thread.sleep(REQ_EXPIRE_TIMEOUT * 2);
        assertEquals(1, smppSession.getSendWindow().cancelAllExpired().size());
        wait.await();

        assertFalse(smppSession.getChannel().isConnected());
        assertFalse(smppSession.getChannel().isOpen());
        assertFalse(smppSession.getChannel().isBound());
        assertEquals(1, server.getChannelConnects());
        assertEquals(1, server.getChannelDisconnects());
        assertEquals(0, server.getConnectionSize());
    }

    @Test
    public void testUnbindOnClosedSession() throws InterruptedException {
        DefaultSmppSession smppSession = bindSync();
        assertNotNull(smppSession);
        smppSession.close();
        unbindSync(smppSession);
    }

    private void unbindSync(SmppSession smppSession) throws InterruptedException {
        CountDownLatch wait = new CountDownLatch(1);
        smppSession.unbindAsync(() -> wait.countDown());
        wait.await();
    }

    private DefaultSmppSession bindSync() throws InterruptedException {
        CountDownLatch wait = new CountDownLatch(1);
        AtomicReference<SmppSession> ref = new AtomicReference<>();
        client.bindAsync(sessionConfig, null, new BindCallback() {
            @Override
            public void onBindSucess(SmppSession smppSession) {
                ref.set(smppSession);
                wait.countDown();
            }

            @Override
            public void onFailure(Reason reason, Throwable t, BaseBindResp response) {
                wait.countDown();
            }
        });

        wait.await();
        return (DefaultSmppSession) ref.get();
    }
}
