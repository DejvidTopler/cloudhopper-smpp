package com.cloudhopper.smpp.pdu;

/*
 * #%L
 * ch-smpp
 * %%
 * Copyright (C) 2009 - 2015 Cloudhopper by Twitter
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import org.jboss.netty.buffer.ChannelBuffer;

public class SubmitSmResp extends BaseSmResp {

    public SubmitSmResp() {
        super(SmppConstants.CMD_ID_SUBMIT_SM_RESP, "submit_sm_resp");
    }

    @Override
    public void readBody(ChannelBuffer buffer) throws UnrecoverablePduException, RecoverablePduException {
        if(getCommandStatus() > 0)
            return;

        super.readBody(buffer);
    }

    @Override
    public void writeBody(ChannelBuffer buffer) throws UnrecoverablePduException, RecoverablePduException {
        if(getCommandStatus() > 0)
            return;

        super.writeBody(buffer);
    }

    @Override
    public int calculateByteSizeOfBody() {
        if(getCommandStatus() > 0)
            return 0;

        return super.calculateByteSizeOfBody();
    }
}