

import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.mypk.controllers.MenuController;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.controllers.CacheController;
import org.airtel.ug.mypk.pojo.MicroBundleRetryRequest;
import org.airtel.ug.mypk.processors.MicroBundleBaseProcessor;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.MOBIQUITY_SUCCESS_CODE;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.OCS_OPERATOR_ID;
import static org.airtel.ug.mypk.processors.MicroBundleBaseProcessor.OCS_SUCCESS_CODE;
import org.airtel.ug.mypk.util.SMSClient;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author Benjamin E Ndugga
 */
public class RetryProcessor extends MicroBundleBaseProcessor implements Runnable {

    private  static final Logger LOGGER = Logger.getLogger("MYPK_EJB");
    
    private MicroBundleRetryRequest retryRequest = null;
    
    

    public RetryProcessor(MicroBundleRetryRequest retryRequest) {

        super();

        this.retryRequest = retryRequest;

        transactionLog.setChannel("RETRY");
    }

    /**
     * process retry requests that are picked off the queue, then pushes them
     * back incase there is a communication failure with Airtel Money
     */
    @Override
    public final void run() {

        CacheController hzClient = new CacheController();

        String msisdn = retryRequest.getMsisdn();
        String sessionId = retryRequest.getSessionId();
        String sourceIp = retryRequest.getSourceIp();
        int optionId = retryRequest.getOptionId();
        String externaId = retryRequest.getExternalId();
        String imsi = retryRequest.getImsi();

        transactionLog.setMsisdn(msisdn);
        transactionLog.setOptionId(optionId);
        transactionLog.setSessionid(sessionId);
        transactionLog.setRequestIp(sourceIp);
        transactionLog.setImsi(imsi);
        transactionLog.setExt_transid(externaId);

        try {

            LOGGER.log(Level.INFO, "LOOKUP_CUSTOMER_BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = hzClient.fetchSubscriberBand(msisdn);

            transactionLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP_MENU_ID_VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuController().getMenuItem(band_id, optionId);

            transactionLog.setOcsProdID(menuItem.getOcsProdId());
            transactionLog.setAmProdId(menuItem.getAmProdId());
            transactionLog.setPrice(menuItem.getPrice());

            //check from AM if this external is present
            MobiquityReponseHandler mobiquityReponseHandler = inquireTransactionStatusOfExtId(externaId, msisdn);

            if (mobiquityReponseHandler.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                //init ocs client object
                OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

                /**
                 * append the product to zero-rental send request OCS
                 */
                SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                prod1.setId(menuItem.getAmProdId());
                prod1.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct[] productList = {prod1};

                ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN", externaId).getResultHeader();

                LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                transactionLog.setOcsResp(resultHeader.getResultCode());
                transactionLog.setOcsDesc(resultHeader.getResultDesc());
                transactionLog.setRequestSerial(ocs.getSerialNo());

                //send subscription failure message
                if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {
                    //roll-back transcation on AM
                    //send notifaction message
                    SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                }

            } else {
                //send failure sms
                //SMSClient.send_sms(msisdn, mobiquityReponseHandler.getMessage());
                SMSClient.send_sms(msisdn, "Dear customer you request for " + menuItem.getMenuItemName() + " failed to be processed.");
            }

        } catch (Exception ex) {

            /**
             * in this section push retry request to the queue for another
             * attempt incase any of these exceptions are thrown.
             *
             */
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            transactionLog.setException_str(ex.getLocalizedMessage());

            /**
             * if the current retry count is higher than 0
             */
            int currentRetryCount = retryRequest.getCurrentRetryCount();

            if (currentRetryCount < MAX_RETRY_COUNT) {

                retryRequest.setCurrentRetryCount(++currentRetryCount);

                //hzClient.addRetryRequestToQueue(retryRequest);
            } else {
                SMSClient.send_sms(msisdn, "Dear Customer, your request failed to be processed, please contact our customer care services.Trasaction Id "+retryRequest.getExternalId());
            }

        } finally {
            logRequest();
        }
    }

}
