package com.cloudhopper.smpp.async;

import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppClient;
import com.cloudhopper.smpp.async.client.DefaultAsyncSmppSession;

/**
 * Created by ib-dtopler on 12.02.16..
 */
public class AsyncClientTestUtils {

    public static DefaultAsyncSmppSession bindSync(DefaultAsyncSmppClient client,
            SmppSessionConfiguration sessionConfig) throws InterruptedException {

        AsyncBindClientAwaiter awaiter = new AsyncBindClientAwaiter();
        awaiter.bind(client, sessionConfig);
        return awaiter.awaitForSessionBound();
    }

}
