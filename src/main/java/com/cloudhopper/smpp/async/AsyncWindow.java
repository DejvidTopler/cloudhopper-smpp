package com.cloudhopper.smpp.async;

import com.cloudhopper.commons.util.windowing.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by ib-dtopler on 18.02.16..
 */
public class AsyncWindow {

    private final ConcurrentMap<Integer, AsyncRequestContext> container = new ConcurrentHashMap<>();

    public void insert(AsyncRequestContext ctx) throws DuplicateKeyException {
        int key = ctx.getRequest().getSequenceNumber();
        AsyncRequestContext previous = container.putIfAbsent(key, ctx);
        if (previous != null)
            throw new DuplicateKeyException("The key [" + key + "] already exists in the window");


        //TODO(DT) check window size

        ctx.setExpireTimestamp(System.currentTimeMillis() + ctx.getWindowTimeout());
    }

    public AsyncRequestContext complete(int key) {
        return container.remove(key);
    }

    public List<AsyncRequestContext> cancelAll() {
        List<AsyncRequestContext> ret = new ArrayList<>();
        container.forEach((key, asyncRequestContext) -> {
            AsyncRequestContext val = container.remove(key);
            if (val != null)
                ret.add(val);
        });
        return ret;
    }

    public int getSize() {
        return container.size();
    }

    public List<AsyncRequestContext> cancelAllExpired() {
        long now = System.currentTimeMillis();
        List<AsyncRequestContext> ret = new ArrayList<>();
        container.forEach((key, ctx) -> {
            if (now > ctx.getExpireTimestamp()) {
                AsyncRequestContext expired = container.remove(key);
                if (expired != null) {
                    ret.add(expired);
                }
            }
        });

        ret.forEach((ctx) -> {
            if (ctx.getCallback() != null)
                ctx.getCallback().onExpire();
        });

        return ret;
    }
}
