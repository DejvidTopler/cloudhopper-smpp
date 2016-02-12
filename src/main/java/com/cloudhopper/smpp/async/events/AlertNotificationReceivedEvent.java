package com.cloudhopper.smpp.async.events;

import com.cloudhopper.smpp.pdu.AlertNotification;

/**
 * Created by ib-dtopler on 11.02.16..
 */
public class AlertNotificationReceivedEvent implements SessionEvent {

    private final AlertNotification alertNotification;

    public AlertNotificationReceivedEvent(AlertNotification alertNotification) {
        this.alertNotification = alertNotification;
    }

    public AlertNotification getAlertNotification() {
        return alertNotification;
    }
}