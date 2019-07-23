package org.airtel.ug.mypk.service;

import org.airtel.ug.mypk.util.SMSClient;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.io.IOException;
import java.util.logging.Level;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.hz.HZClient;
import org.airtel.ug.mypk.util.MenuHandler;
import org.airtel.ug.mypk.util.MenuItem;
import org.airtel.ug.mypk.util.MyPakalastBundleException;
import org.airtel.ug.mypk.util.MicroBundleProcessorUtil;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author benjamin
 */
public class RequestProcessor extends MicroBundleProcessorUtil implements Runnable {

    private final String msisdn;
    private final String sessionId;
    private final String sourceIp;
    private final String imsi;
    private String pin = null;
    private final int optionId;

    public RequestProcessor(String msisdn, String sessionId, int optionId, String sourceIp, String imsi, String pin) {

        super();

        this.msisdn = msisdn;
        this.sessionId = sessionId;
        this.optionId = optionId;
        this.sourceIp = sourceIp;
        this.imsi = imsi;
        this.pin = pin;

        requestLog.setChannel("USSD");

    }

    public RequestProcessor(String msisdn, String sessionId, int optionId, String sourceIp, String imsi) {

        super();

        this.msisdn = msisdn;
        this.sessionId = sessionId;
        this.optionId = optionId;
        this.sourceIp = sourceIp;
        this.imsi = imsi;

        requestLog.setChannel("USSD");

    }

    @Override
    public void run() {

        HZClient hzClient = new HZClient();

        try {

            OCSWebMethods ocs = new OCSWebMethods(OCS_IP, OCS_PORT);

            requestLog.setMsisdn(msisdn);
            requestLog.setSessionid(sessionId);
            requestLog.setOptionId(optionId);
            requestLog.setRequestIp(sourceIp);
            requestLog.setImsi(imsi);

            LOGGER.log(Level.INFO, "LOOKUP_CUSTOMER_BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = hzClient.getBand(msisdn);

            requestLog.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP_MENU_ID_VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            requestLog.setOcsProdID(menuItem.getOcsProdId());
            requestLog.setAmProdId(menuItem.getAmProdId());
            requestLog.setPrice(menuItem.getPrice());

            LOGGER.log(Level.INFO, "SELECTED_MENU_ITEM {0} | {1}", new Object[]{menuItem, msisdn});

            if (pin != null) {

                //send charge on Airtel Money
                MobiquityReponseHandler mbqtResp = debitMobiquityAccount(msisdn.substring(3), menuItem, pin);

                if (mbqtResp.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                    //append the product to zero-rental 
                    //send request OCS
                    SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                    prod1.setId(menuItem.getAmProdId());
                    prod1.setValidMode(ValidMode.value1);

                    SubscribeAppendantProductRequestProduct[] productList = {prod1};

                    ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID + "_ATL_MN").getResultHeader();

                    LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                    LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                    requestLog.setOcsResp(resultHeader.getResultCode());
                    requestLog.setOcsDesc(resultHeader.getResultDesc());
                    requestLog.setRequestSerial(ocs.getSerialNo());

                    //send subscription failure message
                    if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {
                        //roll-back transcation on AM
                        //send notifaction message
                        SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                    }

                } else {
                    //send failure sms
                    SMSClient.send_sms(msisdn, mbqtResp.getMessage());
                }

            } else {

                //send request OCS
                SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                prod1.setId(menuItem.getOcsProdId());
                prod1.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct[] productList = {prod1};

                ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID).getResultHeader();

                requestLog.setOcsResp(resultHeader.getResultCode());
                requestLog.setOcsDesc(resultHeader.getResultDesc());
                requestLog.setRequestSerial(ocs.getSerialNo());

                LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                //send subscription failure message
                if (!resultHeader.getResultCode().equals(OCS_SUCCESS_CODE)) {
                    //roll-back transcation on AM
                    //send notifaction message
                    SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                }
            }

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } catch (IOException | NamingException | ParserConfigurationException | ServiceException ex) {

            SMSClient.send_sms(requestLog.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            requestLog.setException_str(ex.getLocalizedMessage());

        } finally {

            logRequest();

            //clear session data
            hzClient.clearSessionData(msisdn);
        }
    }
}//end of class
