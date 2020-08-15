package org.airtel.ug.mypk.processors;

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
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.pojo.MicroBundleRequest;
import org.airtel.ug.mypk.pojo.MicroBundleRetryRequest;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequestFileHandler;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class RequestProcessor extends BaseProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(RequestProcessor.class.getName());

    private final MicroBundleRequest microBundleRequest;
    private final MenuItem menuItem;

    public RequestProcessor(MicroBundleRequest microBundleRequest, MenuItem menuItem) {
        this.microBundleRequest = microBundleRequest;
        this.menuItem = menuItem;

        transactionLog.setMsisdn(microBundleRequest.getMsisdn());
        transactionLog.setImsi(microBundleRequest.getImsi());
        transactionLog.setServiceClass(microBundleRequest.getServiceClass());
        transactionLog.setSessionid(microBundleRequest.getSessionId());
        transactionLog.setBand_id(microBundleRequest.getBandId());
        transactionLog.setOptionId(microBundleRequest.getOptionId());
        transactionLog.setRequestIp(microBundleRequest.getSourceIp());
        transactionLog.setImsi(microBundleRequest.getImsi());

        transactionLog.setOcsProdID(menuItem.getOcsProdId());
        transactionLog.setAmProdId(menuItem.getAmProdId());
        transactionLog.setPrice(menuItem.getPrice());

        transactionLog.setChannel("USSD");
    }

    private void subscribeUsingAirtime() {

        LOGGER.log(Level.INFO, "SUBSCRIBE-USING-AT | {0}", microBundleRequest.getMsisdn());

        String internalSessionId;
        try {

            OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM {0} | {1}", new Object[]{menuItem.printLogFormat(), microBundleRequest.getMsisdn()});

            internalSessionId = generateInternalSessionId();

            transactionLog.setRequestSerial(internalSessionId);

            LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{menuItem.getOcsProdId(), microBundleRequest.getMsisdn()});

            validateServiceClass();

            //send request to OCS
            SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
            prod1.setId(menuItem.getOcsProdId());
            prod1.setValidMode(ValidMode.value1);

            SubscribeAppendantProductRequestProduct[] productList = {prod1};

            ResultHeader resultHeader = ocs.subscribeAppendantProduct(microBundleRequest.getMsisdn().substring(3), productList, OCS_OPERATOR_ID, internalSessionId).getResultHeader();

            transactionLog.setOcsResp(resultHeader.getResultCode());
            transactionLog.setOcsDesc(resultHeader.getResultDesc());

            LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), microBundleRequest.getMsisdn()});
            LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), microBundleRequest.getMsisdn()});

            //send subscription failure message
            if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                //send notifaction message
                SMSClient.send_sms(microBundleRequest.getMsisdn(), "Your subscription for " + menuItem.getMenuItemName() + " failed. " + resultHeader.getResultDesc());
            }

        } catch (RemoteException | ServiceException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + microBundleRequest.getMsisdn(), ex);

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(microBundleRequest.getMsisdn(), "Your subscription for " + menuItem.getMenuItemName() + " failed. " + ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + microBundleRequest.getMsisdn(), ex);

        } finally {

            logRequest();

        }
    }

    private void subscribeUsingAirtelMoney() {

        LOGGER.log(Level.INFO, "SUBSCRIBE-USING-AM | {0}", microBundleRequest.getMsisdn());

        String internalSessionId = null;
        try {

            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM {0} | {1}", new Object[]{menuItem.printLogFormat(), microBundleRequest.getMsisdn()});

            internalSessionId = generateInternalSessionId();

            transactionLog.setExt_transid(internalSessionId);
            transactionLog.setRequestSerial(internalSessionId);

            LOGGER.log(Level.INFO, "SETTING-MQT-TRANS-ID {0} | {1}", new Object[]{internalSessionId, microBundleRequest.getMsisdn()});

            validateServiceClass();

            //send charge on Airtel Money
            MobiquityReponseHandler mbqtResp = debitMobiquityAccount(microBundleRequest.getMsisdn().substring(3), menuItem, microBundleRequest.getPin(), internalSessionId);

            if (mbqtResp.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{menuItem.getAmProdId(), microBundleRequest.getMsisdn()});

                //append the product to zero-rental 
                //send request OCS
                SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                prod1.setId(menuItem.getAmProdId());
                prod1.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct[] productList = {prod1};

                OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

                ResultHeader resultHeader = ocs.subscribeAppendantProduct(microBundleRequest.getMsisdn().substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN", internalSessionId).getResultHeader();

                transactionLog.setOcsResp(resultHeader.getResultCode());
                transactionLog.setOcsDesc(resultHeader.getResultDesc());

                LOGGER.log(Level.INFO, "OCS-RESP-DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), microBundleRequest.getMsisdn()});
                LOGGER.log(Level.INFO, "OCS-RESP-CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), microBundleRequest.getMsisdn()});

                //send subscription failure message
                if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                    //send failure for retry
                    MicroBundleRetryRequest microBundleRetryRequest = new MicroBundleRetryRequest();

                    microBundleRetryRequest.setMsisdn(microBundleRequest.getMsisdn());
                    microBundleRetryRequest.setSessionId(microBundleRequest.getSessionId());
                    microBundleRetryRequest.setExternalId(internalSessionId);
                    microBundleRetryRequest.setOptionId(microBundleRequest.getOptionId());
                    microBundleRetryRequest.setBandId(microBundleRequest.getBandId());
                    microBundleRetryRequest.setSourceIp(microBundleRequest.getSourceIp());
                    microBundleRetryRequest.setImsi(microBundleRequest.getImsi());
                    microBundleRetryRequest.setMenuItem(menuItem);

                    new MicroBundleRetryRequestFileHandler()
                            .writeRetryTransaction(microBundleRetryRequest);
                }

            } else {
                //send failure sms
                SMSClient.send_sms(microBundleRequest.getMsisdn(), "Your request for "+menuItem.getMenuItemName()+" failed. "+ mbqtResp.getMessage());
            }

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(microBundleRequest.getMsisdn(), "Your subscription for " + menuItem.getMenuItemName() + " failed. " + ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + microBundleRequest.getMsisdn(), ex);

        } catch (DebitAccountException | ServiceException | RemoteException ex) {

            SMSClient.send_sms(microBundleRequest.getMsisdn(), "Your request is being processed at the moment, please wait for a confirmation sms.");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + microBundleRequest.getMsisdn(), ex);

            transactionLog.setException_str(ex.getLocalizedMessage());

            MicroBundleRetryRequest microBundleRetryRequest = new MicroBundleRetryRequest();

            microBundleRetryRequest.setMsisdn(microBundleRequest.getMsisdn());
            microBundleRetryRequest.setSessionId(microBundleRequest.getSessionId());
            microBundleRetryRequest.setExternalId(internalSessionId);
            microBundleRetryRequest.setOptionId(microBundleRequest.getOptionId());
            microBundleRetryRequest.setSourceIp(microBundleRequest.getSourceIp());
            microBundleRetryRequest.setImsi(microBundleRequest.getImsi());
            microBundleRetryRequest.setMenuItem(menuItem);

            new MicroBundleRetryRequestFileHandler()
                    .writeRetryTransaction(microBundleRetryRequest);

        } finally {

            logRequest();

        }

    }

    @Override
    public void run() {

        LOGGER.log(Level.INFO, "CALLING-RUN-METHOD | {0}", microBundleRequest.getMsisdn());

        if (microBundleRequest.getPin() != null) {
            subscribeUsingAirtelMoney();
        } else {
            subscribeUsingAirtime();
        }
    }

    private void validateServiceClass() throws MyPakalastBundleException {
        //if the service class is not set on this request
        if (microBundleRequest.getServiceClass() == null) {
            throw new MyPakalastBundleException("Missing Service class in voice bundle request.", 127);
        }

        //check if service class is excluded
        if (EXLUDED_SERVICE_CLASSES.contains(microBundleRequest.getServiceClass())) {
            throw new MyPakalastBundleException("Subscriber Service class excluded from this bundle.", 128);
        }
    }

    @Override
    public String toString() {
        return "MicroBundleRequestProcessor{" + "microBundleRequest=" + microBundleRequest + ", menuItem=" + menuItem + '}';
    }

}//end of class
