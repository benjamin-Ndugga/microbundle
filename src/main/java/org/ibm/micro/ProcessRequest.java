package org.ibm.micro;

import com.huawei.www.bme.cbsinterface.cbs.businessmgr.SubscribeAppendantProductRequestProduct;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.ValidMode;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.am.MobiquityReponseHandler;
import org.ibm.hz.HZClient;
import org.ibm.logger.SubscriptionLog;
import org.ibm.ussd.MenuHandler;
import org.ibm.ussd.MicroBundleMenuItem;
import org.ibm.ussd.MicroBundleException;
import org.ibm.ws.OCSWebMethods;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author benjamin
 */
public class ProcessRequest implements Runnable {

    private static final Logger LOGGER = Logger.getLogger("MICRO_BUNDLE_REQ");

    private final SubscriptionLog subscriptionInfo = new SubscriptionLog();

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
            subscriptionInfo.setChannel("AIRTEL_MONEY");

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

            MicroBundleMenuItem menuItem = new MenuHandler().getMenuItem(band_id, optionId);

            LOGGER.log(Level.INFO, "SELECTED_MENU_ITEM {0} | {1}", new Object[]{menuItem, msisdn});

            if (pin != null) {

                subscriptionInfo.setVoiceSmsProdId(menuItem.getAmVoiceSmsProdId());
                subscriptionInfo.setDataProdId(menuItem.getDataProId());
                subscriptionInfo.setPrice(menuItem.getPrice());

                //check if is are products configured for AM
                if (menuItem.getAmVoiceSmsProdId() == null || menuItem.getDataProId() == null) {

                    LOGGER.log(Level.SEVERE, "MISSING-PRODUCT-CONFIGURATION | {0}", msisdn);

                    throw new MicroBundleException("Missing Product Configuration!");
                }

                //send charge on Airtel Money
                MobiquityReponseHandler mbqtResp = debitMobiquityAccount(msisdn.substring(3), menuItem, this.pin);

                if (mbqtResp.getTxnstatus().equals("200")) {

                    //append the product to zero-rental 
                    //send request OCS
                    SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                    prod1.setId(menuItem.getAmVoiceSmsProdId());
                    prod1.setValidMode(ValidMode.value1);

                    LOGGER.log(Level.INFO, "SETTING-AM-PROD1 {0} | {1}", new Object[]{menuItem.getAmVoiceSmsProdId(), msisdn});

                    SubscribeAppendantProductRequestProduct prod2 = new SubscribeAppendantProductRequestProduct();
                    prod2.setId(menuItem.getDataProId());
                    prod2.setValidMode(ValidMode.value1);

                    LOGGER.log(Level.INFO, "SETTING-AM-PROD2 {0} | {1}", new Object[]{menuItem.getDataProId(), msisdn});

                    SubscribeAppendantProductRequestProduct[] productList = {prod1, prod2};

                    ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, "MicroBundle").getResultHeader();

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

                subscriptionInfo.setVoiceSmsProdId(menuItem.getOcsVoiceSmsProdId());
                subscriptionInfo.setDataProdId(menuItem.getDataProId());
                subscriptionInfo.setPrice(menuItem.getPrice());

                //send request OCS
                SubscribeAppendantProductRequestProduct prod1 = new SubscribeAppendantProductRequestProduct();
                prod1.setId(menuItem.getOcsVoiceSmsProdId());
                prod1.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct prod2 = new SubscribeAppendantProductRequestProduct();
                prod2.setId(menuItem.getDataProId());
                prod2.setValidMode(ValidMode.value1);

                SubscribeAppendantProductRequestProduct[] productList = {prod1, prod2};

                ResultHeader resultHeader = ocs.subscribeAppendantProduct(msisdn.substring(3), productList, "MicroBundle").getResultHeader();

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

        } catch (MicroBundleException ex) {

            subscriptionInfo.setException_string(ex.getLocalizedMessage());

            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());

            LOGGER.log(Level.INFO, "{0} | {1}", new Object[]{ex.getLocalizedMessage(), msisdn});

        } catch (ParserConfigurationException | NamingException | IndexOutOfBoundsException | NumberFormatException | ServiceException | IOException | SAXException ex) {

            subscriptionInfo.setException_string(ex.getLocalizedMessage());

            SMSClient.send_sms(subscriptionInfo.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + msisdn, ex);

        } finally {

            //log request regardless of an exception or not
            logRequest();

            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            //clear session data
            hzClient.clearSessionData(msisdn);
        }

    }

    private void logRequest() {
        Connection connection = null;
        InitialContext ic = null;

        try {

            LOGGER.log(Level.INFO, "LOGGING_REQUEST | {0}", subscriptionInfo.getMsisdn());

            ic = new InitialContext();

            DataSource dataSource = (DataSource) ic.lookup("KIKADB");
            connection = (Connection) dataSource.getConnection();

            PreparedStatement statement = connection.prepareStatement("INSERT INTO MICRO_SUBSCRIPTIONS ("
                    + "MSISDN,"
                    + "IMSI,"
                    + "SESSIONID,"
                    + "BAND_ID,"
                    + "OCS_RESP,"
                    + "OCS_DESC,"
                    + "REQUEST_IP,"
                    + "PROCESSING_NODE,"
                    + "OPTION_ID,"
                    + "REQUEST_SERIAL,"
                    + "PRODUCT_ID,"
                    + "PRODUCT_ID2,"
                    + "PRICE,"
                    + "MOBIQUITY_CODE,"
                    + "MOBIQUITY_DESC,"
                    + "MOBIQUITY_TRANS_ID,"
                    + "EXT_TRANSID,"
                    + "MOBIQUITY_XML_RESP,"
                    + "EXCEPTION_STR) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

            statement.setString(1, subscriptionInfo.getMsisdn());
            statement.setString(2, subscriptionInfo.getImsi());
            statement.setString(3, subscriptionInfo.getSessionid());
            statement.setInt(4, subscriptionInfo.getBand_id());
            statement.setString(5, subscriptionInfo.getOcsResp());
            statement.setString(6, subscriptionInfo.getOcsDesc());
            statement.setString(7, subscriptionInfo.getRequestIp());
            statement.setString(8, subscriptionInfo.getProcessingNode());
            statement.setInt(9, subscriptionInfo.getOptionId());
            statement.setString(10, subscriptionInfo.getRequestSerial());
            statement.setString(11, subscriptionInfo.getVoiceSmsProdId());
            statement.setString(12, subscriptionInfo.getDataProdId());
            statement.setInt(13, subscriptionInfo.getPrice());
            statement.setString(14, subscriptionInfo.getMobiquity_code());
            statement.setString(15, subscriptionInfo.getMobiquity_desc());
            statement.setString(16, subscriptionInfo.getMobiquity_transid());
            statement.setString(17, subscriptionInfo.getExt_transid());
            statement.setString(18, subscriptionInfo.getMobiquity_xml_resp());
            statement.setString(19, subscriptionInfo.getException_string());

            int i = statement.executeUpdate();

            connection.commit();

            LOGGER.log(Level.INFO, "EXECUTE_UPDATE_RESPONSE {0} | {1}", new Object[]{i, subscriptionInfo.getMsisdn()});

        } catch (NullPointerException | NamingException | SQLException ex) {

            SMSClient.send_sms(subscriptionInfo.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");

            LOGGER.log(Level.INFO, null, ex + " | " + subscriptionInfo.getMsisdn());

        } finally {

            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    private MobiquityReponseHandler debitMobiquityAccount(String msisdn, MicroBundleMenuItem menuItem, String pin) throws MalformedURLException, RemoteException, ParserConfigurationException, NamingException, SAXException, IOException {

        OutputStream output = null;
        BufferedReader reader = null;
        InitialContext ic = null;

        try {

            ic = new InitialContext();

            //test-ip-port --- > http://172.27.77.135:3777
            String AM_IP_PORT = (String) ic.lookup("resource/am/socket");
            //1009020
            String COLLECTION_ACCOUNTS = (String) ic.lookup("resource/am/voice/nickname");
            //String COLLECTION_ACCOUNTS = "100335";
            //test
            String MBQT_USERNAME = (String) ic.lookup("resource/am/user");
            //test/@123
            String MBQT_PASSWORD = (String) ic.lookup("resource/am/pass");

            LOGGER.log(Level.INFO, "AM_IP_PORT {0} | {1}", new Object[]{AM_IP_PORT, msisdn});

            String mqtTransId = getMqtTransId();

            LOGGER.log(Level.INFO, "SETTING_MQT_TRANS_ID {0} | {1}", new Object[]{mqtTransId, msisdn});

            String url = AM_IP_PORT;
            String charset = "UTF-8";

            String request = "<COMMAND>\n"
                    + "<TYPE>PAYBILLREQ</TYPE>\n"
                    + "<serviceType>PAYBILLREQ</serviceType>\n"
                    + "<interfaceId>UGBPP</interfaceId>\n"
                    + "<MSISDN>" + msisdn + "</MSISDN>\n"
                    + "<MSISDN2>" + COLLECTION_ACCOUNTS + "</MSISDN2>\n"
                    + "<AMOUNT>" + menuItem.getPrice() + "</AMOUNT>\n"
                    + "<PIN>" + pin + "</PIN>\n"
                    + "<EXTTRID>" + mqtTransId + "</EXTTRID>\n"
                    + "<TXNTYPE>MERCHANT</TXNTYPE>\n"
                    + "<REFERENCE1>" + menuItem.getAmVoiceSmsProdId() + "-" + menuItem.getMenuItemName() + "</REFERENCE1>\n"
                    + "<USERNAME>" + MBQT_USERNAME + "</USERNAME>\n"
                    + "<PASSWORD>" + MBQT_PASSWORD + "</PASSWORD>\n"
                    + "</COMMAND>";

            URLConnection connection = new URL(url).openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept-Charset", charset);
            connection.setRequestProperty("Content-Type", "text/xml");

            output = connection.getOutputStream();
            output.write(request.getBytes(charset));
            output.flush();

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            StringBuilder xmlresponse = new StringBuilder();

            while ((inputLine = reader.readLine()) != null) {
                xmlresponse.append(inputLine);
            }

            subscriptionInfo.setMobiquity_xml_resp(xmlresponse.toString());

            LOGGER.log(Level.INFO, "AM_RESPONSE: {0} | {1}", new Object[]{xmlresponse.toString(), msisdn});

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            MobiquityReponseHandler mobiquityReponseHandler = new MobiquityReponseHandler();
            saxParser.parse((new InputSource(new StringReader(xmlresponse.toString()))), mobiquityReponseHandler);

            LOGGER.log(Level.INFO, "TXNSTATUS {0} | {1}", new Object[]{mobiquityReponseHandler.getTxnstatus(), msisdn});
            LOGGER.log(Level.INFO, "MESSAGE  {0} | {1}", new Object[]{mobiquityReponseHandler.getMessage(), msisdn});

            subscriptionInfo.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
            subscriptionInfo.setMobiquity_desc(mobiquityReponseHandler.getMessage());
            subscriptionInfo.setMobiquity_transid(mobiquityReponseHandler.getTxnid());

            return mobiquityReponseHandler;

        } finally {
            try {
                if (output != null) {
                    output.close();
                }

                if (reader != null) {
                    reader.close();
                }

                if (ic != null) {
                    ic.close();
                }
            } catch (IOException | NamingException ex) {
                LOGGER.log(Level.INFO, ex.getLocalizedMessage(), ex);
            }
        }
    }

    private String getMqtTransId() {
        //randomise the id
        char alphabet[] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
            'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
            'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
        Random random = new Random();

        String externalId = (alphabet[random.nextInt(alphabet.length)] + "" + random.nextInt(100000000));

        subscriptionInfo.setExt_transid(externalId);

        LOGGER.log(Level.INFO, "MOBIQUIY-EXTERNAL ID {0} | {1}", new Object[]{externalId, msisdn});

        return externalId;
    }

}//end of class
