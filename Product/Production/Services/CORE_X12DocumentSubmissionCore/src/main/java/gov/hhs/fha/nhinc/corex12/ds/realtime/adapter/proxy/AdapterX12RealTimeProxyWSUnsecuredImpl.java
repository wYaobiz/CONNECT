/*
 * Copyright (c) 2009-2018, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gov.hhs.fha.nhinc.corex12.ds.realtime.adapter.proxy;

import gov.hhs.fha.nhinc.adaptercore.AdapterCORETransactionPortType;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommonadapter.AdapterCOREEnvelopeRealTimeRequestType;
import gov.hhs.fha.nhinc.common.nhinccommonadapter.AdapterCOREEnvelopeRealTimeResponseType;
import gov.hhs.fha.nhinc.corex12.ds.realtime.adapter.proxy.service.AdapterX12RealTimeUnsecuredServicePortDescriptor;
import gov.hhs.fha.nhinc.corex12.ds.utils.X12AdapterExceptionBuilder;
import gov.hhs.fha.nhinc.messaging.client.CONNECTClient;
import gov.hhs.fha.nhinc.messaging.client.CONNECTClientFactory;
import gov.hhs.fha.nhinc.messaging.service.port.ServicePortDescriptor;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import gov.hhs.fha.nhinc.webserviceproxy.WebServiceProxyHelper;
import org.caqh.soap.wsdl.corerule2_2_0.COREEnvelopeRealTimeRequest;
import org.caqh.soap.wsdl.corerule2_2_0.COREEnvelopeRealTimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cmay
 */
public class AdapterX12RealTimeProxyWSUnsecuredImpl implements AdapterX12RealTimeProxy {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterX12RealTimeProxyWSUnsecuredImpl.class);
    private WebServiceProxyHelper proxyHelper = null;

    public AdapterX12RealTimeProxyWSUnsecuredImpl() {
        proxyHelper = new WebServiceProxyHelper();
    }

    @Override
    public COREEnvelopeRealTimeResponse realTimeTransaction(COREEnvelopeRealTimeRequest msg, AssertionType assertion) {
        COREEnvelopeRealTimeResponse response;

        try {
            String url = proxyHelper.getAdapterEndPointFromConnectionManager(
                NhincConstants.ADAPTER_CORE_X12DS_REALTIME_SERVICE_NAME);

            if (NullChecker.isNotNullish(url)) {
                ServicePortDescriptor<AdapterCORETransactionPortType> portDescriptor
                    = new AdapterX12RealTimeUnsecuredServicePortDescriptor();

                CONNECTClient<AdapterCORETransactionPortType> client = CONNECTClientFactory.getInstance()
                    .getCONNECTClientUnsecured(portDescriptor, url, assertion);

                AdapterCOREEnvelopeRealTimeRequestType requestWrapper = new AdapterCOREEnvelopeRealTimeRequestType();
                requestWrapper.setCOREEnvelopeRealTimeRequest(msg);

                AdapterCOREEnvelopeRealTimeResponseType responseWrapper
                    = (AdapterCOREEnvelopeRealTimeResponseType) client.invokePort(
                        AdapterCORETransactionPortType.class, "realTimeTransaction", requestWrapper);

                response = responseWrapper.getCOREEnvelopeRealTimeResponse();
            } else {
                response = X12AdapterExceptionBuilder.getInstance().buildCOREEnvelopeRealTimeErrorResponse(msg);
                LOG.error("Failed to call the web service ({}); the URL is null.",
                    NhincConstants.ADAPTER_CORE_X12DS_REALTIME_SERVICE_NAME);
            }
        } catch (Exception ex) {
            // TODO: Add error handling based on CORE X12 DS RealTime use cases, e.g., adapter not found, timeout, etc.
            LOG.error("Error sending Adapter CORE X12 Doc Submission Unsecured message: {}",
                ex.getLocalizedMessage(), ex);
            response = new COREEnvelopeRealTimeResponse();
            response.setErrorMessage(NhincConstants.CORE_X12DS_ACK_ERROR_MSG);
            response.setErrorCode(NhincConstants.CORE_X12DS_ACK_ERROR_CODE);
        }

        return response;
    }
}