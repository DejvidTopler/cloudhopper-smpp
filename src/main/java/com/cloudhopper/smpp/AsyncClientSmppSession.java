package com.cloudhopper.smpp;

import com.cloudhopper.smpp.async.callback.BindCallback;
import com.cloudhopper.smpp.async.session.AsyncSmppSession;
import com.cloudhopper.smpp.pdu.BaseBind;

/**
 * Created by ib-dtopler on 08.02.16..
 */
public interface AsyncClientSmppSession extends AsyncSmppSession {

    void bind(BaseBind request, BindCallback bindCallback);

}
