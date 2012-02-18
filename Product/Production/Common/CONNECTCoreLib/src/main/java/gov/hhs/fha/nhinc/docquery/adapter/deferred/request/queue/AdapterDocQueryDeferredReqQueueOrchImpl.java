/*
 * Copyright (c) 2012, United States Government, as represented by the Secretary of Health and Human Services. 
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
package gov.hhs.fha.nhinc.docquery.adapter.deferred.request.queue;

import java.util.List;

import gov.hhs.fha.nhinc.async.AsyncMessageIdCreator;
import gov.hhs.fha.nhinc.async.AsyncMessageProcessHelper;
import gov.hhs.fha.nhinc.asyncmsgs.dao.AsyncMsgRecordDao;
import gov.hhs.fha.nhinc.common.nhinccommon.AcknowledgementType;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.HomeCommunityType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetCommunitiesType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetSystemType;
import gov.hhs.fha.nhinc.common.nhinccommonadapter.RespondingGatewayCrossGatewayQueryRequestType;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManagerCache;
import gov.hhs.fha.nhinc.connectmgr.UrlInfo;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManagerException;
import gov.hhs.fha.nhinc.docquery.DocQueryAuditLog;
import gov.hhs.fha.nhinc.docquery.DocQueryPolicyChecker;
import gov.hhs.fha.nhinc.docquery.adapter.proxy.AdapterDocQueryProxyJavaImpl;
import gov.hhs.fha.nhinc.docquery.passthru.deferred.response.proxy.PassthruDocQueryDeferredResponseProxy;
import gov.hhs.fha.nhinc.docquery.passthru.deferred.response.proxy.PassthruDocQueryDeferredResponseProxyObjectFactory;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import gov.hhs.fha.nhinc.transform.document.DocQueryAckTranforms;
import gov.hhs.fha.nhinc.util.HomeCommunityMap;
import gov.hhs.healthit.nhin.DocQueryAcknowledgementType;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * @author narendra.reddy
 */
public class AdapterDocQueryDeferredReqQueueOrchImpl {

    private static final Log log = LogFactory.getLog(AdapterDocQueryDeferredReqQueueOrchImpl.class);

    protected AsyncMessageProcessHelper createAsyncProcesser() {
        return new AsyncMessageProcessHelper();
    }

    /**
     * 
     * @param msg
     * @param assertion
     * @param targets
     * @return DocQueryAcknowledgementType
     */
    public DocQueryAcknowledgementType respondingGatewayCrossGatewayQuery(AdhocQueryRequest msg,
            AssertionType assertion, NhinTargetCommunitiesType targets) {
        log.debug("Begin AdapterDocQueryDeferredReqQueueOrchImpl.respondingGatewayCrossGatewayQuery");

        DocQueryAcknowledgementType respAck = new DocQueryAcknowledgementType();
        String ackMsg = "";

        RespondingGatewayCrossGatewayQueryRequestType respondingGatewayCrossGatewayQueryRequestType = new RespondingGatewayCrossGatewayQueryRequestType();
        respondingGatewayCrossGatewayQueryRequestType.setAdhocQueryRequest(msg);
        respondingGatewayCrossGatewayQueryRequestType.setAssertion(assertion);

        String homeCommunityId = HomeCommunityMap.getLocalHomeCommunityId();

        // Audit the incoming doc query request Message
        DocQueryAuditLog auditLogger = new DocQueryAuditLog();
        AcknowledgementType ack = auditLogger.auditDQRequest(msg, assertion,
                NhincConstants.AUDIT_LOG_INBOUND_DIRECTION, NhincConstants.AUDIT_LOG_ENTITY_INTERFACE, homeCommunityId);

        // ASYNCMSG PROCESSING - RSPPROCESS
        AsyncMessageProcessHelper asyncProcess = createAsyncProcesser();
        String messageId = assertion.getMessageId();

        // Generate a new response assertion
        AssertionType responseAssertion = asyncProcess.copyAssertionTypeObject(assertion);
        // Original request message id is now set as the relates to id
        responseAssertion.getRelatesToList().add(messageId);
        // Generate a new unique response assertion Message ID
        responseAssertion.setMessageId(AsyncMessageIdCreator.generateMessageId());
        // Set user info homeCommunity
        HomeCommunityType homeCommunityType = new HomeCommunityType();
        homeCommunityType.setHomeCommunityId(homeCommunityId);
        homeCommunityType.setName(homeCommunityId);
        responseAssertion.setHomeCommunity(homeCommunityType);

        boolean bIsQueueOk = asyncProcess.processMessageStatus(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPPROCESS);

        if (bIsQueueOk) {
            try {
                List<UrlInfo> urlInfoList = getEndpoints(targets);

                if (urlInfoList != null && NullChecker.isNotNullish(urlInfoList) && urlInfoList.get(0) != null
                        && NullChecker.isNotNullish(urlInfoList.get(0).getHcid())
                        && NullChecker.isNotNullish(urlInfoList.get(0).getUrl())) {

                    HomeCommunityType targetHcid = new HomeCommunityType();
                    targetHcid.setHomeCommunityId(urlInfoList.get(0).getHcid());

                    if (isPolicyValid(msg, assertion, targetHcid)) {
                        NhinTargetSystemType target = new NhinTargetSystemType();
                        target.setUrl(urlInfoList.get(0).getUrl());

                        // Audit the Adhoc Query Request Message sent to the Adapter Interface
                        ack = auditAdhocQueryRequest(respondingGatewayCrossGatewayQueryRequestType,
                                NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION,
                                NhincConstants.AUDIT_LOG_ADAPTER_INTERFACE, homeCommunityId);

                        // Get the AdhocQueryResponse by passing the request to this agency's adapter doc query service
                        AdapterDocQueryProxyJavaImpl orchImpl = new AdapterDocQueryProxyJavaImpl();
                        AdhocQueryResponse response = orchImpl
                                .respondingGatewayCrossGatewayQuery(
                                        respondingGatewayCrossGatewayQueryRequestType.getAdhocQueryRequest(),
                                        responseAssertion);

                        // Audit the Adhoc Query Response Message sent to the Adapter Interface
                        ack = auditAdhocQueryResponse(response, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION,
                                NhincConstants.AUDIT_LOG_ADAPTER_INTERFACE, assertion, homeCommunityId);

                        PassthruDocQueryDeferredResponseProxyObjectFactory factory = new PassthruDocQueryDeferredResponseProxyObjectFactory();
                        PassthruDocQueryDeferredResponseProxy proxy = factory
                                .getPassthruDocQueryDeferredResponseProxy();

                        respAck = proxy.respondingGatewayCrossGatewayQuery(response, responseAssertion, target);

                        asyncProcess.processAck(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTACK,
                                AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR, respAck);
                    } else {
                        ackMsg = "Outgoing Policy Check Failed";
                        log.error(ackMsg);
                        respAck = DocQueryAckTranforms.createAckMessage(
                                NhincConstants.DOC_QUERY_DEFERRED_RESP_ACK_FAILURE_STATUS_MSG,
                                NhincConstants.DOC_QUERY_DEFERRED_ACK_ERROR_AUTHORIZATION, ackMsg);
                        asyncProcess.processAck(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR,
                                AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR, respAck);
                    }
                } else {
                    ackMsg = "Failed to obtain target URL from connection manager";
                    log.error(ackMsg);
                    respAck = DocQueryAckTranforms.createAckMessage(
                            NhincConstants.DOC_QUERY_DEFERRED_RESP_ACK_FAILURE_STATUS_MSG,
                            NhincConstants.DOC_QUERY_DEFERRED_ACK_ERROR_INVALID, ackMsg);
                    asyncProcess.processAck(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR,
                            AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR, respAck);
                }
            } catch (Exception e) {
                ackMsg = "Exception processing Deferred Query For Documents: " + e.getMessage();
                log.error(ackMsg, e);
                respAck = DocQueryAckTranforms.createAckMessage(
                        NhincConstants.DOC_QUERY_DEFERRED_RESP_ACK_FAILURE_STATUS_MSG,
                        NhincConstants.DOC_QUERY_DEFERRED_ACK_ERROR_INVALID, ackMsg);
                asyncProcess.processAck(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR,
                        AsyncMsgRecordDao.QUEUE_STATUS_RSPSENTERR, respAck);
            }
        } else {
            ackMsg = "Deferred Query For Documents response processing halted; deferred queue repository error encountered";

            // Set the error acknowledgement status
            // fatal error with deferred queue repository
            respAck = DocQueryAckTranforms.createAckMessage(
                    NhincConstants.DOC_QUERY_DEFERRED_RESP_ACK_FAILURE_STATUS_MSG,
                    NhincConstants.DOC_QUERY_DEFERRED_ACK_ERROR_INVALID, ackMsg);
        }

        // Audit the responding Acknowledgement Message
        ack = auditLogger.logDocQueryAck(respAck, responseAssertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION,
                NhincConstants.AUDIT_LOG_ENTITY_INTERFACE, homeCommunityId);

        return respAck;
    }

    /**
     * 
     * @param message
     * @param assertion
     * @param target
     * @return boolean
     */
    private boolean isPolicyValid(AdhocQueryRequest message, AssertionType assertion, HomeCommunityType target) {
        boolean policyIsValid = new DocQueryPolicyChecker().checkOutgoingRequestPolicy(message, assertion, target);

        return policyIsValid;
    }

    /**
     * 
     * @param targetCommunities
     * @return List<UrlInfo>
     */
    private List<UrlInfo> getEndpoints(NhinTargetCommunitiesType targetCommunities) {
        List<UrlInfo> urlInfoList = null;

        try {
            urlInfoList = ConnectionManagerCache.getInstance().getEndpointURLFromNhinTargetCommunities(
                    targetCommunities, NhincConstants.NHIN_DOCUMENT_QUERY_DEFERRED_RESP_SERVICE_NAME);
        } catch (ConnectionManagerException ex) {
            log.error("Failed to obtain target URLs", ex);
        }

        return urlInfoList;
    }

    /**
     * Creates an audit log for an AdhocQueryRequest.
     * 
     * @param crossGatewayDocQueryRequest AdhocQueryRequest message to log
     * @param direction Indicates whether the message is going out or comming in
     * @param _interface Indicates which interface component is being logged??
     * @return Returns an acknowledgement object indicating whether the audit was successfully completed.
     */
    private AcknowledgementType auditAdhocQueryRequest(RespondingGatewayCrossGatewayQueryRequestType msg,
            String direction, String _interface, String requestCommunityID) {
        DocQueryAuditLog auditLogger = new DocQueryAuditLog();
        AcknowledgementType ack = auditLogger.auditDQRequest(msg.getAdhocQueryRequest(), msg.getAssertion(), direction,
                _interface, requestCommunityID);

        return ack;
    }

    /**
     * Creates an audit log for an AdhocQueryResponse.
     * 
     * @param crossGatewayDocQueryResponse AdhocQueryResponse message to log
     * @param direction Indicates whether the message is going out or comming in
     * @param _interface Indicates which interface component is being logged??
     * @param requestCommunityID
     * @return Returns an acknowledgement object indicating whether the audit was successfully completed.
     */
    private AcknowledgementType auditAdhocQueryResponse(AdhocQueryResponse msg, String direction, String _interface,
            AssertionType assertion, String requestCommunityID) {
        DocQueryAuditLog auditLogger = new DocQueryAuditLog();
        AcknowledgementType ack = auditLogger
                .auditDQResponse(msg, assertion, direction, _interface, requestCommunityID);

        return ack;
    }

}
