package org.airtel.ug.mypk.service;

import org.airtel.ug.mypk.util.SMSClient;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.mypk.exceptions.DebitAccountException;
import org.airtel.ug.mypk.exceptions.SubscribeBundleException;
import org.airtel.ug.mypk.exceptions.TransactionStatusException;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.retry.RetryRequest;
import org.airtel.ug.mypk.retry.RetryRequestFileHandler;
import org.airtel.ug.mypk.util.HzClient;
import org.airtel.ug.mypk.util.MyPakalastBundleException;
import org.airtel.ug.mypk.util.MicroBundleProcessorUtil;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class RequestProcessor extends MicroBundleProcessorUtil implements Runnable {

    private static final Logger LOGGER = Logger.getLogger("MYPK");

    private final String msisdn;
    private final String sessionId;
    private final String sourceIp;
    private final String imsi;
    private final Integer optionId;
    
    private String pin = null;
    private RetryRequest retryRequest = null;

    public RequestProcessor(RetryRequest retryRequest) {
        this.retryRequest = retryRequest;

        this.msisdn = retryRequest.getMsisdn();
        this.sessionId = retryRequest.getSessionId();
        this.sourceIp = retryRequest.getSourceIp();
        this.optionId = retryRequest.getOptionId();

        this.imsi = retryRequest.getImsi();

        requestLog.setChannel("RETRY");
        
        LOGGER.log(Level.INFO, "REQUEST-SENT-FROM {0} | {1}", new Object[]{sourceIp, msisdn});
    }

    public RequestProcessor(String msisdn, String sessionId, int optionId, String sourceIp, String imsi, String pin) {

        this.msisdn = msisdn;
        this.sessionId = sessionId;
        this.optionId = optionId;
        this.sourceIp = sourceIp;
        this.imsi = imsi;
        this.pin = pin;

        requestLog.setChannel("USSD");

        LOGGER.log(Level.INFO, "REQUEST-SENT-FROM {0} | {1}", new Object[]{sourceIp, msisdn});
        
    }

    private void subscribeViaAirtime() {

        LOGGER.log(Level.INFO, "SUBSCRIBE-USING-AT | {0}", msisdn);

        HzClient hzClient = new HzClient();
        String internalSessionId;
        try {

            OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

            requestLog.setMsisdn(msisdn);
            requestLog.setSessionid(sessionId);
            requestLog.setOptionId(optionId);
            requestLog.setRequestIp(sourceIp);
            requestLog.setImsi(imsi);

            LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = hzClient.getBand(msisdn);

            requestLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP-MENU-ID-VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            requestLog.setOcsProdID(menuItem.getOcsProdId());
            requestLog.setAmProdId(menuItem.getAmProdId());
            requestLog.setPrice(menuItem.getPrice());

            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM {0} | {1}", new Object[]{menuItem.printLogFormat(), msisdn});

            internalSessionId = generateInternalSessionId();

            LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{menuItem.getOcsProdId(), msisdn});
            
            //send request to OCS
            SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
            prod1.setId(menuItem.getOcsProdId());
            prod1.setValidMode(ValidMode.value1);

            SubscribeAppendantProductRequestProduct[] productList = {prod1};

            ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID, internalSessionId).getResultHeader();

            requestLog.setOcsResp(resultHeader.getResultCode());
            requestLog.setOcsDesc(resultHeader.getResultDesc());
            requestLog.setRequestSerial(ocs.getSerialNo());

            LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
            LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

            //send subscription failure message
            if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                //send notifaction message
                SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
            }

        } catch (RemoteException | ServiceException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } finally {

            logRequest();

            //clear session data
            hzClient.clearSessionData(msisdn);
        }
    }

    private void subscribeViaAirtelMoney() {

        LOGGER.log(Level.INFO, "SUBSCRIBE-USING-AM | {0}", msisdn);

        HzClient hzClient = new HzClient();
        String internalSessionId = null;
        try {

            requestLog.setMsisdn(msisdn);
            requestLog.setSessionid(sessionId);
            requestLog.setOptionId(optionId);
            requestLog.setRequestIp(sourceIp);
            requestLog.setImsi(imsi);

            LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = hzClient.getBand(msisdn);

            requestLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP-MENU-ID-VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            requestLog.setOcsProdID(menuItem.getOcsProdId());
            requestLog.setAmProdId(menuItem.getAmProdId());
            requestLog.setPrice(menuItem.getPrice());

            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM {0} | {1}", new Object[]{menuItem.printLogFormat(), msisdn});

            internalSessionId = generateInternalSessionId();

            requestLog.setExt_transid(internalSessionId);

            LOGGER.log(Level.INFO, "SETTING-MQT-TRANS-ID {0} | {1}", new Object[]{internalSessionId, msisdn});

            //send charge on Airtel Money
            MobiquityReponseHandler mbqtResp = debitMobiquityAccount(msisdn.substring(3), menuItem, pin, internalSessionId);

            if (mbqtResp.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{menuItem.getAmProdId(), msisdn});
                
                //append the product to zero-rental 
                //send request OCS
                SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                prod1.setId(menuItem.getAmProdId());
                prod1.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct[] productList = {prod1};

                ResultHeader resultHeader = null;

                try {

                    OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);
                    requestLog.setRequestSerial(internalSessionId);

                    resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN", internalSessionId).getResultHeader();

                    requestLog.setOcsResp(resultHeader.getResultCode());
                    requestLog.setOcsDesc(resultHeader.getResultDesc());

                    LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                    LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                } catch (RemoteException | ServiceException ex) {

                    throw new SubscribeBundleException("OCS: " + ex.getLocalizedMessage());
                }

                //send subscription failure message
                if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                    //send notifaction message
                    SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                    //send failure for retry

                }

            } else {
                //send failure sms
                SMSClient.send_sms(msisdn, mbqtResp.getMessage());
            }

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } catch (DebitAccountException | SubscribeBundleException ex) {

            SMSClient.send_sms(msisdn, "Your request is being processed at the moment, please wait for a confirmation sms.");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            requestLog.setException_str(ex.getLocalizedMessage());

            RetryRequest rt = new RetryRequest();
            rt.setMsisdn(msisdn);
            rt.setSessionId(sessionId);
            rt.setExternalId(internalSessionId);
            rt.setOptionId(optionId);
            rt.setSourceIp(sourceIp);
            rt.setImsi(imsi);

            new RetryRequestFileHandler().writeRetryTransaction(retryRequest);

        } finally {

            logRequest();

            //clear session data
            hzClient.clearSessionData(msisdn);
        }

    }

    private void processRetryRequest() {

        String externalId = retryRequest.getExternalId();

        requestLog.setMsisdn(msisdn);
        requestLog.setOptionId(optionId);
        requestLog.setSessionid(sessionId);
        requestLog.setRequestIp(sourceIp);
        requestLog.setImsi(imsi);
        requestLog.setExt_transid(externalId);

        try {
            LOGGER.log(Level.INFO, "PROCESS-RETRY-REQUEST | {0}", msisdn);

            //delete file from the filesystem
            new RetryRequestFileHandler().deletRetryFile(externalId + ".ser");

            LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = new HzClient().getBand(msisdn);

            requestLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP-MENU-ID-VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            requestLog.setOcsProdID(menuItem.getOcsProdId());
            requestLog.setAmProdId(menuItem.getAmProdId());
            requestLog.setPrice(menuItem.getPrice());

            LOGGER.log(Level.INFO, "MENU-FOUND: {0} | {1}", new Object[]{menuItem.printLogFormat(), msisdn});

            //check from AM if this external is present
            MobiquityReponseHandler mobiquityReponseHandler = inquireTransactionStatusOfExtId(externalId, msisdn);

            if (mobiquityReponseHandler.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                try {
                    //init ocs client object
                    OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

                    LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{menuItem.getAmProdId(), msisdn});
                    
                    SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                    prod1.setId(menuItem.getAmProdId());
                    prod1.setValidMode(ValidMode.value1);

                    SubscribeAppendantProductRequestProduct[] productList = {prod1};

                    requestLog.setRequestSerial(externalId);

                    ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN", externalId).getResultHeader();
                    requestLog.setOcsResp(resultHeader.getResultCode());
                    requestLog.setOcsDesc(resultHeader.getResultDesc());

                    LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                    LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                    //send subscription failure message
                    if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {
                        //roll-back transcation on AM
                        //send notifaction message
                        SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                    }

                } catch (RemoteException | ServiceException ex) {
                    throw new SubscribeBundleException("OCS: " + ex.getLocalizedMessage());
                }

            } else {
                //send failure sms
                //SMSClient.send_sms(msisdn, mobiquityReponseHandler.getMessage());
                SMSClient.send_sms(msisdn, "Dear customer you request for " + menuItem.getMenuItemName() + " failed to be processed.");
            }

        } catch (SubscribeBundleException | TransactionStatusException | MyPakalastBundleException ex) {

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

                new RetryRequestFileHandler().writeRetryTransaction(retryRequest);
            } else {
                SMSClient.send_sms(msisdn, "Dear Customer, your request failed to be processed, please contact our customer care services.Trasaction Id " + retryRequest.getExternalId());
            }

        } finally {
            logRequest();
        }

    }

    @Override
    public void run() {
        if (pin != null) {
            subscribeViaAirtelMoney();
        } else if (retryRequest != null) {
            processRetryRequest();
        } else {
            subscribeViaAirtime();
        }
    }
}//end of class
