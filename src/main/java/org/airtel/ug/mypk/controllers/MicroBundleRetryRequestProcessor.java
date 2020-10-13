package org.airtel.ug.mypk.controllers;

import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import static org.airtel.ug.mypk.controllers.MicroBundleBaseProcessor.MOBIQUITY_SUCCESS_CODE;
import static org.airtel.ug.mypk.controllers.MicroBundleBaseProcessor.OCS_OPERATOR_ID;
import static org.airtel.ug.mypk.controllers.MicroBundleBaseProcessor.OCS_SUCCESS_CODE;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.exceptions.SubscribeBundleException;
import org.airtel.ug.mypk.exceptions.TransactionStatusException;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequest;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequestFileHandler;
import org.airtel.ug.mypk.util.MicroBundleHzClient;
import org.airtel.ug.mypk.util.SMSClient;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class MicroBundleRetryRequestProcessor extends MicroBundleBaseProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleRetryRequestProcessor.class.getName());

    private final MicroBundleRetryRequest retryRequest;

    public MicroBundleRetryRequestProcessor(MicroBundleRetryRequest retryRequest) {
        this.retryRequest = retryRequest;

        requestLog.setMsisdn(retryRequest.getMsisdn());
        requestLog.setOptionId(retryRequest.getOptionId());
        requestLog.setSessionid(retryRequest.getSessionId());
        requestLog.setRequestIp(retryRequest.getSourceIp());
        requestLog.setImsi(retryRequest.getImsi());
        requestLog.setChannel("RETRY");
        requestLog.setBand_id(retryRequest.getBand_id());
        requestLog.setExt_transid(retryRequest.getExternalId());

        requestLog.setOcsProdID(retryRequest.getOcsProdId());
        requestLog.setAmProdId(retryRequest.getAmProdId());
        requestLog.setPrice(retryRequest.getPrice());

    }

    @Override
    public void run() {
        processRetryRequest();
    }

    private void processRetryRequest() {

        LOGGER.log(Level.INFO, "PROCESS-RETRY-REQUEST | {0}", retryRequest);

        String msisdn = retryRequest.getMsisdn();
        String externalId = retryRequest.getExternalId();
        String amProdId = retryRequest.getAmProdId();

        //delete file from the filesystem
        MicroBundleRetryRequestFileHandler microBundleRetryRequestFileHandler = new MicroBundleRetryRequestFileHandler();

        microBundleRetryRequestFileHandler.deletRetryFile(externalId);

        //delete from the DB
        microBundleRetryRequestFileHandler.deleteRetryRequest(retryRequest);

        try {

            MobiquityReponseHandler mobiquityReponseHandler = new MobiquityReponseHandler();

            //check if there was a retry sent to AM before
            if (retryRequest.getMobiquity_code() == null) {

                LOGGER.log(Level.INFO, "NO-MOBIQUITY-RESPONSE | {0}", msisdn);

                LOGGER.log(Level.INFO, "SEND-TRANSACTION-INQUIRY | {0}", msisdn);

                //check from AM if this external is present
                mobiquityReponseHandler = inquireTransactionStatusOfExtId(retryRequest.getExternalId(), msisdn);

                //add this information to the retry object
                retryRequest.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
                retryRequest.setMobiquity_desc(mobiquityReponseHandler.getMessage());
                retryRequest.setMobiquity_transid(mobiquityReponseHandler.getTxnid());
                retryRequest.setMobiquity_xml_resp(requestLog.getMobiquity_xml_resp());

            } else {

                LOGGER.log(Level.INFO, "RECORD-HAS-MOBIQUITY-RESPONSE | {0}", msisdn);

                //add retry log information
                requestLog.setMobiquity_code(retryRequest.getMobiquity_code());
                requestLog.setMobiquity_desc(retryRequest.getMobiquity_desc());
                requestLog.setMobiquity_transid(retryRequest.getMobiquity_transid());
                requestLog.setMobiquity_xml_resp(retryRequest.getMobiquity_xml_resp());

            }

            if (mobiquityReponseHandler.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE) || retryRequest.getMobiquity_code().equals(MOBIQUITY_SUCCESS_CODE)) {

                requestLog.setRequestSerial(externalId);

                ResultHeader resultHeader = subscribePakalastBundle(amProdId, msisdn, externalId, OCS_OPERATOR_ID + "_ATL_MN");

                //send subscription failure message
                if (!(resultHeader.getResultCode().equals(OCS_SUCCESS_CODE) || resultHeader.getResultCode().equals(OCS_ALREADY_PROCESSED_CODE))) {

                    LOGGER.log(Level.INFO, "SENDING-FOR-RETRY | {0}", msisdn);

                    int currentRetryCount = retryRequest.getCurrentRetryCount();

                    if (currentRetryCount < MAX_RETRY_COUNT) {

                        retryRequest.setCurrentRetryCount(++currentRetryCount);

                        new MicroBundleRetryRequestFileHandler()
                                .writeRetryTransaction(retryRequest);
                    } else {
                        SMSClient.send_sms(msisdn, "Dear Customer, your request failed to be processed, please contact our customer care services.Trasaction Id " + retryRequest.getExternalId());
                    }

                }

            } else {
                //send failure sms
                //SMSClient.send_sms(msisdn, mobiquityReponseHandler.getMessage());
                SMSClient.send_sms(msisdn, "Dear customer you request for " + retryRequest.getBundleName() + " failed to be processed.");
            }

        } catch (SubscribeBundleException | TransactionStatusException ex) {

            /**
             * in this section push retry request to the queue for another
             * attempt incase any of these exceptions are thrown.
             *
             */
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            requestLog.setException_str(ex.getLocalizedMessage());

            /**
             * if the current retry count is higher than 0
             */
            int currentRetryCount = retryRequest.getCurrentRetryCount();

            if (currentRetryCount < MAX_RETRY_COUNT) {

                retryRequest.setCurrentRetryCount(++currentRetryCount);

                new MicroBundleRetryRequestFileHandler().writeRetryTransaction(retryRequest);
            } else {
                SMSClient.send_sms(msisdn, "Dear Customer, your request failed to be processed, please contact our customer care services.Transaction Id: " + retryRequest.getExternalId());
            }

        } finally {
            logRequest();
        }

    }

}
