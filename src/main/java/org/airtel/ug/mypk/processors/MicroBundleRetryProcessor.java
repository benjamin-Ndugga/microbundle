package org.airtel.ug.mypk.processors;

import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.mypk.controllers.CacheController;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.exceptions.SubscribeBundleException;
import org.airtel.ug.mypk.exceptions.TransactionStatusException;
import org.airtel.ug.mypk.controllers.MenuController;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.pojo.MicroBundleRetryRequest;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.MOBIQUITY_SUCCESS_CODE;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.OCS_OPERATOR_ID;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.OCS_SUCCESS_CODE;
import org.airtel.ug.mypk.retry.MicroBundleRetryRequestFileHandler;
import org.airtel.ug.mypk.util.SMSClient;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class MicroBundleRetryProcessor extends MicroBundleBaseProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleRetryProcessor.class.getName());

    private final MicroBundleRetryRequest microBundleRetryRequest;

    public MicroBundleRetryProcessor(MicroBundleRetryRequest microBundleRetryRequest) {
        this.microBundleRetryRequest = microBundleRetryRequest;

        transactionLog.setMsisdn(microBundleRetryRequest.getMsisdn());
        transactionLog.setBand_id(microBundleRetryRequest.getBandId());
        transactionLog.setOptionId(microBundleRetryRequest.getOptionId());
        transactionLog.setSessionid(microBundleRetryRequest.getSessionId());
        transactionLog.setRequestIp(microBundleRetryRequest.getSourceIp());
        transactionLog.setImsi(microBundleRetryRequest.getImsi());
        transactionLog.setExt_transid(microBundleRetryRequest.getExternalId());
        transactionLog.setRequestSerial(microBundleRetryRequest.getExternalId());

        transactionLog.setOcsProdID(microBundleRetryRequest.getMenuItem().getOcsProdId());
        transactionLog.setAmProdId(microBundleRetryRequest.getMenuItem().getAmProdId());
        transactionLog.setPrice(microBundleRetryRequest.getMenuItem().getPrice());

        transactionLog.setChannel("RETRY");
    }

    @Override
    public void run() {
        processRetryRequest();
    }

    private void processRetryRequest() {

        try {
            LOGGER.log(Level.INFO, "PROCESS-RETRY-REQUEST | {0}", microBundleRetryRequest.getMsisdn());

            //delete file from the filesystem
            new MicroBundleRetryRequestFileHandler()
                    .deletRetryFile(microBundleRetryRequest.getExternalId());

            LOGGER.log(Level.INFO, "MENU-FOUND: {0} | {1}", new Object[]{microBundleRetryRequest.getMenuItem().printLogFormat(), microBundleRetryRequest.getMsisdn()});

            //check from AM if this external is present
            MobiquityReponseHandler mobiquityReponseHandler = inquireTransactionStatusOfExtId(microBundleRetryRequest.getExternalId(), microBundleRetryRequest.getMsisdn());

            if (mobiquityReponseHandler.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                try {
                    //init ocs client object
                    OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

                    LOGGER.log(Level.INFO, "SETTING-PROD-ID: {0} | {1}", new Object[]{microBundleRetryRequest.getMenuItem().getAmProdId(), microBundleRetryRequest.getMsisdn()});

                    SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                    prod1.setId(microBundleRetryRequest.getMenuItem().getAmProdId());
                    prod1.setValidMode(ValidMode.value1);
                    SubscribeAppendantProductRequestProduct[] productList = {prod1};

                    ResultHeader resultHeader = ocs.subscribeAppendantProduct(microBundleRetryRequest.getMsisdn().substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN", microBundleRetryRequest.getExternalId()).getResultHeader();

                    transactionLog.setOcsResp(resultHeader.getResultCode());
                    transactionLog.setOcsDesc(resultHeader.getResultDesc());

                    LOGGER.log(Level.INFO, "OCS-RESP-DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), microBundleRetryRequest.getMsisdn()});
                    LOGGER.log(Level.INFO, "OCS-RESP-CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), microBundleRetryRequest.getMsisdn()});

                    //send subscription failure message
                    if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {

                        LOGGER.log(Level.INFO, "SENDING-FOR-RETRY | {0}", microBundleRetryRequest.getMsisdn());

                        int currentRetryCount = microBundleRetryRequest.getCurrentRetryCount();

                        if (currentRetryCount < MAX_RETRY_COUNT) {

                            microBundleRetryRequest.setCurrentRetryCount(++currentRetryCount);

                            new MicroBundleRetryRequestFileHandler().writeRetryTransaction(microBundleRetryRequest);
                        } else {
                            SMSClient.send_sms(microBundleRetryRequest.getMsisdn(), "Dear Customer, your request failed to be processed, please contact our customer care services.Trasaction Id " + microBundleRetryRequest.getExternalId());
                        }

                    }

                } catch (RemoteException | ServiceException ex) {
                    throw new SubscribeBundleException("OCS: " + ex.getLocalizedMessage());
                }

            } else {
                //send failure sms
                //SMSClient.send_sms(msisdn, mobiquityReponseHandler.getMessage());
                SMSClient.send_sms(microBundleRetryRequest.getMsisdn(), "Dear customer you request for " + microBundleRetryRequest.getMenuItem().getMenuItemName() + " failed to be processed.");
            }

        } catch (SubscribeBundleException | TransactionStatusException ex) {

            /**
             * in this section push retry request to the queue for another
             * attempt incase any of these exceptions are thrown.
             *
             */
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + microBundleRetryRequest.getMsisdn(), ex);

            transactionLog.setException_str(ex.getLocalizedMessage());

            /**
             * if the current retry count is higher than 0
             */
            int currentRetryCount = microBundleRetryRequest.getCurrentRetryCount();

            if (currentRetryCount < MAX_RETRY_COUNT) {

                microBundleRetryRequest.setCurrentRetryCount(++currentRetryCount);

                new MicroBundleRetryRequestFileHandler().writeRetryTransaction(microBundleRetryRequest);
            } else {
                SMSClient.send_sms(microBundleRetryRequest.getMsisdn(), "Dear Customer, your request failed to be processed, please contact our customer care services.Transaction Id: " + microBundleRetryRequest.getExternalId());
            }

        } finally {
            logRequest();
        }

    }

}
