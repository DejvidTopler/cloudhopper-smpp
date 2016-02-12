package com.cloudhopper.smpp.async.events;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class ExceptionThrownEvent implements SessionEvent {

    private final Throwable cause;

    public
    ExceptionThrownEvent(Throwable cause) {
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}