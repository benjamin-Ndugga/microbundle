/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.airtel.ug.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
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
import org.airtel.ug.am.MobiquityReponseHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Benjamin
 */
public class ProcessUtil {

    public static final Logger LOGGER = Logger.getLogger("MYPAKALAST");
    public static final String MOBIQUITY_SUCCESS_CODE = "200";

    public final SubscriptionLog subscriptionInfo = new SubscriptionLog();

    public final void logRequest() {
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
            statement.setString(11, subscriptionInfo.getOcsProdID());
            statement.setString(12, subscriptionInfo.getAmProdId());
            statement.setInt(13, subscriptionInfo.getPrice());
            statement.setString(14, subscriptionInfo.getMobiquity_code());
            statement.setString(15, subscriptionInfo.getMobiquity_desc());
            statement.setString(16, subscriptionInfo.getMobiquity_transid());
            statement.setString(17, subscriptionInfo.getExt_transid());
            statement.setString(18, subscriptionInfo.getMobiquity_xml_resp());
            statement.setString(19, subscriptionInfo.getException_str());

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

    public final MobiquityReponseHandler debitMobiquityAccount(String msisdn, MenuItem menuItem, String pin) throws MalformedURLException, IOException, ParserConfigurationException, NamingException {

        OutputStream output = null;
        BufferedReader reader = null;
        InitialContext ic = null;

        try {

            ic = new InitialContext();

            //test-ip-port --- > http://172.27.77.135:3777
            String AM_IP_PORT = (String) ic.lookup("resource/am/socket");
            String MBQT_PREFUNDED_ACC = (String) ic.lookup("resource/am/voice/nickname");
            //String MBQT_PREFUNDED_ACC = "1063002";
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
                    + "<MSISDN2>" + MBQT_PREFUNDED_ACC + "</MSISDN2>\n"
                    + "<AMOUNT>" + menuItem.getPrice() + "</AMOUNT>\n"
                    + "<PIN>" + pin + "</PIN>\n"
                    + "<EXTTRID>" + mqtTransId + "</EXTTRID>\n"
                    + "<TXNTYPE>MERCHANT</TXNTYPE>\n"
                    + "<REFERENCE1>" + menuItem.getAmProdId() + "-" + menuItem.getMenuItemName() + "</REFERENCE1>\n"
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

        } catch (SAXException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            return new MobiquityReponseHandler();

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

    public final String getMqtTransId() {
        //randomise the id
        char alphabet[] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
            'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
            'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
        Random random = new Random();

        String externalId = (alphabet[random.nextInt(alphabet.length)] + "" + random.nextInt(100000000));

        subscriptionInfo.setExt_transid(externalId);

        LOGGER.log(Level.INFO, "MOBIQUIY-EXTERNAL ID {0} ", new Object[]{externalId});

        return externalId;
    }

}
