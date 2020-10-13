package org.airtel.ug.mypk.controllers;

import org.airtel.ug.mypk.util.SMSClient;
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
import org.airtel.ug.mypk.exceptions.DebitAccountException;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.exceptions.SubscribeBundleException;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequest;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequestFileHandler;
import org.airtel.ug.mypk.util.MicroBundleHzClient;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class MicroBundleRequestProcessor extends MicroBundleBaseProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleRequestProcessor.class.getName());

    private final String msisdn;
    private final String sessionId;
    private final String sourceIp;
    private final String imsi;
    private final Integer optionId;

    private String pin = null;

    public MicroBundleRequestProcessor(String msisdn, String sessionId, int optionId, String sourceIp, String imsi, String pin) {

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

        MicroBundleHzClient hzClient = new MicroBundleHzClient();
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
            //hzClient.clearSessionData(msisdn);
        }
    }

    private void subscribeViaAirtelMoney() {

        LOGGER.log(Level.INFO, "SUBSCRIBE-USING-AM | {0}", msisdn);

        MicroBundleHzClient hzClient = new MicroBundleHzClient();
        String internalSessionId = null;
        int band_id = 10;//by default
        MenuItem menuItem = null;
        ResultHeader resultHeader = null;
        try {

            requestLog.setMsisdn(msisdn);
            requestLog.setSessionid(sessionId);
            requestLog.setOptionId(optionId);
            requestLog.setRequestIp(sourceIp);
            requestLog.setImsi(imsi);

            LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", msisdn);

            //get the band for this customer
            band_id = hzClient.getBand(msisdn);

            requestLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP-MENU-ID-VALUE {0} | {1}", new Object[]{optionId, msisdn});

            menuItem = new MenuHandler().getMenuItem(band_id, optionId);

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

                resultHeader = subscribePakalastBundle(menuItem.getAmProdId(), msisdn, internalSessionId, OCS_OPERATOR_ID + "_ATL_MN");

                //send subscription failure message
                if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                    //send failure for retry
                    MicroBundleRetryRequest microBundleRetryRequest = new MicroBundleRetryRequest();

                    microBundleRetryRequest.setMsisdn(msisdn);
                    microBundleRetryRequest.setSessionId(sessionId);
                    microBundleRetryRequest.setExternalId(internalSessionId);
                    microBundleRetryRequest.setOptionId(optionId);
                    microBundleRetryRequest.setSourceIp(sourceIp);
                    microBundleRetryRequest.setImsi(imsi);
                    microBundleRetryRequest.setOcs_resp_code(resultHeader.getResultCode());
                    microBundleRetryRequest.setOcs_resp_desc(resultHeader.getResultDesc());
                    microBundleRetryRequest.setBand_id(band_id);
                    microBundleRetryRequest.setBundleName(menuItem.getMenuItemName());
                    microBundleRetryRequest.setOcsProdId(menuItem.getOcsProdId());
                    microBundleRetryRequest.setAmProdId(menuItem.getAmProdId());
                    microBundleRetryRequest.setPrice(menuItem.getPrice());
                    microBundleRetryRequest.setProcessing_node(requestLog.getProcessingNode());

                    microBundleRetryRequest.setMobiquity_code(mbqtResp.getTxnstatus());
                    microBundleRetryRequest.setMobiquity_desc(mbqtResp.getMessage());
                    microBundleRetryRequest.setMobiquity_transid(mbqtResp.getTxnid());
                    microBundleRetryRequest.setMobiquity_xml_resp(requestLog.getMobiquity_xml_resp());

                    new MicroBundleRetryRequestFileHandler()
                            .writeRetryTransaction(microBundleRetryRequest);
                }

            } else {
                //send failure sms
                SMSClient.send_sms(msisdn, mbqtResp.getMessage());
            }

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } catch (SubscribeBundleException ex) {

            SMSClient.send_sms(msisdn, "Your request is being processed at the moment, please wait for a confirmation sms.");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            requestLog.setException_str(ex.getLocalizedMessage());

            MicroBundleRetryRequest microBundleRetryRequest = new MicroBundleRetryRequest();

            microBundleRetryRequest.setMsisdn(msisdn);
            microBundleRetryRequest.setSessionId(sessionId);
            microBundleRetryRequest.setExternalId(internalSessionId);
            microBundleRetryRequest.setOptionId(optionId);
            microBundleRetryRequest.setSourceIp(sourceIp);
            microBundleRetryRequest.setImsi(imsi);
            microBundleRetryRequest.setBand_id(band_id);
            microBundleRetryRequest.setBundleName(menuItem.getMenuItemName());
            microBundleRetryRequest.setOcsProdId(menuItem.getOcsProdId());
            microBundleRetryRequest.setAmProdId(menuItem.getAmProdId());
            microBundleRetryRequest.setPrice(menuItem.getPrice());
            microBundleRetryRequest.setProcessing_node(requestLog.getProcessingNode());

            microBundleRetryRequest.setMobiquity_code(requestLog.getMobiquity_code());
            microBundleRetryRequest.setMobiquity_desc(requestLog.getMobiquity_desc());
            microBundleRetryRequest.setMobiquity_transid(requestLog.getMobiquity_transid());
            microBundleRetryRequest.setMobiquity_xml_resp(requestLog.getMobiquity_xml_resp());

            new MicroBundleRetryRequestFileHandler()
                    .writeRetryTransaction(microBundleRetryRequest);

        } catch (DebitAccountException ex) {

            SMSClient.send_sms(msisdn, "Your request is being processed at the moment, please wait for a confirmation sms.");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            requestLog.setException_str(ex.getLocalizedMessage());

            MicroBundleRetryRequest microBundleRetryRequest = new MicroBundleRetryRequest();
            
            microBundleRetryRequest.setMsisdn(msisdn);
            microBundleRetryRequest.setSessionId(sessionId);
            microBundleRetryRequest.setExternalId(internalSessionId);
            microBundleRetryRequest.setOptionId(optionId);
            microBundleRetryRequest.setSourceIp(sourceIp);
            microBundleRetryRequest.setImsi(imsi);
            microBundleRetryRequest.setBand_id(band_id);
            microBundleRetryRequest.setBundleName(menuItem.getMenuItemName());
            microBundleRetryRequest.setOcsProdId(menuItem.getOcsProdId());
            microBundleRetryRequest.setAmProdId(menuItem.getAmProdId());
            microBundleRetryRequest.setPrice(menuItem.getPrice());
            microBundleRetryRequest.setProcessing_node(requestLog.getProcessingNode());

            new MicroBundleRetryRequestFileHandler()
                    .writeRetryTransaction(microBundleRetryRequest);

        } finally {
            logRequest();
        }
    }

    @Override
    public void run() {
        if (pin != null) {
            subscribeViaAirtelMoney();
        } else {
            subscribeViaAirtime();
        }
    }
}//end of class
