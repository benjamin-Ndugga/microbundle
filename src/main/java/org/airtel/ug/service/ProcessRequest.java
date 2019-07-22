package org.airtel.ug.service;

import org.airtel.ug.util.SMSClient;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.io.IOException;
import java.util.logging.Level;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.am.MobiquityReponseHandler;
import org.airtel.ug.hz.HZClient;
import org.airtel.ug.util.MenuHandler;
import org.airtel.ug.util.MenuItem;
import org.airtel.ug.util.MyPakalastBundleException;
import org.airtel.ug.util.ProcessUtil;
import org.ibm.ws.OCSWebMethods;

/**
 *
 * @author benjamin
 */
public class ProcessRequest extends ProcessUtil implements Runnable {

    private static final String OCS_OPERATOR_ID = "MicroBundle";

    private String msisdn;
    private String sessionId;
    private String sourceIp;
    private String imsi;
    private String pin = null;
    private int optionId;

    public ProcessRequest(String msisdn, String sessionId, int optionId, String sourceIp, String imsi, String pin) {

        try {

            this.msisdn = msisdn;
            this.sessionId = sessionId;
            this.optionId = optionId;
            this.sourceIp = sourceIp;
            this.imsi = imsi;
            this.pin = pin;

            //set the processing node
            subscriptionInfo.setProcessingNode(java.net.InetAddress.getLocalHost().getHostAddress());
            subscriptionInfo.setChannel("USSD");

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
        }
    }

    public ProcessRequest(String msisdn, String sessionId, int optionId, String sourceIp, String imsi) {

        try {

            this.msisdn = msisdn;
            this.sessionId = sessionId;
            this.optionId = optionId;
            this.sourceIp = sourceIp;
            this.imsi = imsi;

            //set the processing node
            subscriptionInfo.setProcessingNode(java.net.InetAddress.getLocalHost().getHostAddress());
            subscriptionInfo.setChannel("USSD");

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    public void run() {

        InitialContext ic = null;
        HZClient hzClient = new HZClient();

        try {

            ic = new InitialContext();
            String ip = (String) ic.lookup("resource/ocs/ip");
            String port = (String) ic.lookup("resource/ocs/port");

            OCSWebMethods ocs = new OCSWebMethods(ip, port);

            subscriptionInfo.setMsisdn(msisdn);
            subscriptionInfo.setSessionid(sessionId);
            subscriptionInfo.setOptionId(optionId);
            subscriptionInfo.setRequestIp(sourceIp);
            subscriptionInfo.setImsi(imsi);

            LOGGER.log(Level.INFO, "LOOKUP_CUSTOMER_BAND | {0}", msisdn);

            //get the band for this customer
            int band_id = hzClient.getBand(msisdn);

            subscriptionInfo.setBand_id(band_id);

            LOGGER.log(Level.INFO, "LOOKUP_MENU_ID_VALUE {0} | {1}", new Object[]{optionId, msisdn});

            MenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            subscriptionInfo.setOcsProdID(menuItem.getOcsProdId());
            subscriptionInfo.setAmProdId(menuItem.getAmProdId());
            subscriptionInfo.setPrice(menuItem.getPrice());

            LOGGER.log(Level.INFO, "SELECTED_MENU_ITEM {0} | {1}", new Object[]{menuItem, msisdn});

            if (pin != null) {

                //send charge on Airtel Money
                MobiquityReponseHandler mbqtResp = debitMobiquityAccount(msisdn.substring(3), menuItem, this.pin);

                if (mbqtResp.getTxnstatus().equals(MOBIQUITY_SUCCESS_CODE)) {

                    //append the product to zero-rental 
                    //send request OCS
                    SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                    prod1.setId(menuItem.getAmProdId());
                    prod1.setValidMode(ValidMode.value1);

                    SubscribeAppendantProductRequestProduct[] productList = {prod1};

                    ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, OCS_OPERATOR_ID+"_ATL_MN").getResultHeader();

                    LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                    LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                    subscriptionInfo.setOcsResp(resultHeader.getResultCode());
                    subscriptionInfo.setOcsDesc(resultHeader.getResultDesc());
                    subscriptionInfo.setRequestSerial(ocs.getSerialNo());

                    //send subscription failure message
                    if (!resultHeader.getResultCode().equals("405000000")) {
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

                subscriptionInfo.setOcsResp(resultHeader.getResultCode());
                subscriptionInfo.setOcsDesc(resultHeader.getResultDesc());
                subscriptionInfo.setRequestSerial(ocs.getSerialNo());

                LOGGER.log(Level.INFO, "OCS_RESP_DESC {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});
                LOGGER.log(Level.INFO, "OCS_RESP_CODE {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});

                //send subscription failure message
                if (!resultHeader.getResultCode().equals("405000000")) {
                    //roll-back transcation on AM
                    //send notifaction message
                    SMSClient.send_sms(msisdn, resultHeader.getResultDesc());
                }
            }

            logRequest();

        } catch (MyPakalastBundleException ex) {

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } catch (IOException | NamingException | ParserConfigurationException | ServiceException ex) {

            SMSClient.send_sms(subscriptionInfo.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

            subscriptionInfo.setException_str(ex.getLocalizedMessage());

        } finally {
            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

        }

        //clear session data
        hzClient.clearSessionData(msisdn);

    }

}//end of class
