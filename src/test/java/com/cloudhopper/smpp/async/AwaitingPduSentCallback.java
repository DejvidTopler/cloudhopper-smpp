package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.async.callback.DefaultPduSentCallback;
import com.cloudhopper.smpp.pdu.PduResponse;
import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class AwaitingPduSentCallback extends DefaultPduSentCallback {
    private int timeoutInSeconds = 2;
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

    public void setTimeoutInSeconds(int timeoutInSeconds) {
        this.timeoutInSeconds = timeoutInSeconds;
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
        Assert.assertTrue(successWait.await(timeoutInSeconds, TimeUnit.SECONDS));
    }

    public void awaitFailure() throws InterruptedException {
        Assert.assertTrue(failureWait.await(timeoutInSeconds, TimeUnit.SECONDS));
    }


    public void awaitCancel() throws InterruptedException {
        Assert.assertTrue(cancelWait.await(timeoutInSeconds, TimeUnit.SECONDS));
    }

    public void awaitExpire() throws InterruptedException {
        Assert.assertTrue(expireWait.await(timeoutInSeconds, TimeUnit.SECONDS));
    }

    public Throwable getException() {
        return exception.get();
    }
}
