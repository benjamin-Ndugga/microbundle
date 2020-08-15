package org.airtel.ug.mypk.processors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.mypk.exceptions.DebitAccountException;
import org.airtel.ug.mypk.exceptions.TransactionStatusException;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.pojo.MicroBundleTransactionLog;
import org.airtel.ug.mypk.util.HostNameVerifier;
import org.airtel.ug.mypk.util.SMSClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Benjamin E Ndugga
 */
public class BaseProcessor {

    private static final Logger LOGGER = Logger.getLogger(BaseProcessor.class.getName());

    public static final String MOBIQUITY_SUCCESS_CODE = "200";
    public static final String OCS_SUCCESS_CODE = "405000000";

    public static final String OCS_OPERATOR_ID = "MicroBundle";

    public String OCS_IP, OCS_PORT;
    public final MicroBundleTransactionLog transactionLog = new MicroBundleTransactionLog();
    public int MAX_RETRY_COUNT = 5;

    public static final ArrayList<String> EXLUDED_SERVICE_CLASSES = new ArrayList<String>() {
        {
            add("5111116");
            add("5111115");

        }
    };

    static {

        HostNameVerifier.verify();

    }

    public BaseProcessor() {
        InitialContext ctx = null;

        try {

            ctx = new InitialContext();
            OCS_IP = (String) ctx.lookup("resource/ocs/ip");
            OCS_PORT = (String) ctx.lookup("resource/ocs/port");

            transactionLog.setChannel("USSD");

            //set the processing node
            transactionLog.setProcessingNode(java.net.InetAddress.getLocalHost().getHostAddress());

            MAX_RETRY_COUNT = (Integer) ctx.lookup("resource/am/retry");

        } catch (UnknownHostException | NamingException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public final void logRequest() {
        Connection connection = null;
        InitialContext ic = null;

        try {

            LOGGER.log(Level.INFO, "LOGGING-REQUEST | {0}", transactionLog);

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
                    + "EXCEPTION_STR,"
                    + "CHANNEL,"
                    + "SERVICE_CLASS) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

            statement.setString(1, transactionLog.getMsisdn());
            statement.setString(2, transactionLog.getImsi());
            statement.setString(3, transactionLog.getSessionid());
            statement.setInt(4, transactionLog.getBand_id());
            statement.setString(5, transactionLog.getOcsResp());
            statement.setString(6, transactionLog.getOcsDesc());
            statement.setString(7, transactionLog.getRequestIp());
            statement.setString(8, transactionLog.getProcessingNode());
            statement.setInt(9, transactionLog.getOptionId());
            statement.setString(10, transactionLog.getRequestSerial());
            statement.setString(11, transactionLog.getOcsProdID());
            statement.setString(12, transactionLog.getAmProdId());
            statement.setInt(13, transactionLog.getPrice());
            statement.setString(14, transactionLog.getMobiquity_code());
            statement.setString(15, transactionLog.getMobiquity_desc());
            statement.setString(16, transactionLog.getMobiquity_transid());
            statement.setString(17, transactionLog.getExt_transid());
            statement.setString(18, transactionLog.getMobiquity_xml_resp());
            statement.setString(19, transactionLog.getException_str());
            statement.setString(20, transactionLog.getChannel());
            statement.setString(21, transactionLog.getServiceClass());

            int i = statement.executeUpdate();

            connection.commit();

            LOGGER.log(Level.INFO, "EXECUTE_UPDATE_RESPONSE {0} | {1}", new Object[]{i, transactionLog.getMsisdn()});

        } catch (NullPointerException | NamingException | SQLException ex) {

            SMSClient.send_sms(transactionLog.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");
            LOGGER.log(Level.INFO, null, ex + " | " + transactionLog.getMsisdn());

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

    /**
     *
     * @param msisdn
     * @param menuItem
     * @param pin
     * @param externalId
     * @return
     * @throws org.airtel.ug.mypk.exceptions.DebitAccountException
     */
    public final MobiquityReponseHandler debitMobiquityAccount(String msisdn, MenuItem menuItem, String pin, String externalId) throws DebitAccountException {

        OutputStream output = null;
        BufferedReader reader = null;
        InitialContext ic = null;

        try {

            ic = new InitialContext();

            //test-OCS_IP-OCS_PORT --- > http://172.27.77.135:3777
            String AM_IP_PORT = (String) ic.lookup("resource/am/socket");
            String MBQT_PREFUNDED_ACC = (String) ic.lookup("resource/am/voice/nickname");
            //String MBQT_PREFUNDED_ACC = "1063002";
            //test
            String MBQT_USERNAME = (String) ic.lookup("resource/am/user");
            //test/@123
            String MBQT_PASSWORD = (String) ic.lookup("resource/am/pass");

            LOGGER.log(Level.INFO, "AM_IP_PORT {0} | {1}", new Object[]{AM_IP_PORT, msisdn});

            String charset = "UTF-8";

            String request = "<COMMAND>\n"
                    + "<TYPE>PAYBILLREQ</TYPE>\n"
                    + "<serviceType>PAYBILLREQ</serviceType>\n"
                    + "<interfaceId>UGBPP</interfaceId>\n"
                    + "<MSISDN>" + msisdn + "</MSISDN>\n"
                    + "<MSISDN2>" + MBQT_PREFUNDED_ACC + "</MSISDN2>\n"
                    + "<AMOUNT>" + menuItem.getPrice() + "</AMOUNT>\n"
                    + "<PIN>" + pin + "</PIN>\n"
                    + "<EXTTRID>" + externalId + "</EXTTRID>\n"
                    + "<TXNTYPE>MERCHANT</TXNTYPE>\n"
                    + "<REFERENCE1>" + menuItem.getAmProdId() + "-" + menuItem.getMenuItemName() + "</REFERENCE1>\n"
                    + "<USERNAME>" + MBQT_USERNAME + "</USERNAME>\n"
                    + "<PASSWORD>" + MBQT_PASSWORD + "</PASSWORD>\n"
                    + "</COMMAND>";

            URLConnection connection = new URL(AM_IP_PORT).openConnection();
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

            transactionLog.setMobiquity_xml_resp(xmlresponse.toString());

            LOGGER.log(Level.INFO, "AM_RESPONSE: {0} | {1}", new Object[]{xmlresponse.toString(), msisdn});

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            MobiquityReponseHandler mobiquityReponseHandler = new MobiquityReponseHandler();
            saxParser.parse((new InputSource(new StringReader(xmlresponse.toString()))), mobiquityReponseHandler);

            LOGGER.log(Level.INFO, "TXNSTATUS {0} | {1}", new Object[]{mobiquityReponseHandler.getTxnstatus(), msisdn});
            LOGGER.log(Level.INFO, "MESSAGE  {0} | {1}", new Object[]{mobiquityReponseHandler.getMessage(), msisdn});

            transactionLog.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
            transactionLog.setMobiquity_desc(mobiquityReponseHandler.getMessage());
            transactionLog.setMobiquity_transid(mobiquityReponseHandler.getTxnid());

            return mobiquityReponseHandler;

        } catch (IOException | NamingException | ParserConfigurationException | SAXException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            throw new DebitAccountException("MOBIQUITY: " + ex.getLocalizedMessage());

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

    public final String generateInternalSessionId() {

        //String serial = RandomStringUtils.random(2, true, false).toUpperCase() + "" + Math.abs(ThreadLocalRandom.current().nextLong(1, 999999999));
        String serial = RandomStringUtils.random(11, true, true).toUpperCase() + "MICRO";

        LOGGER.log(Level.INFO, "GENERATE-SERIAL: {0}", serial);

        return serial;
    }

    /**
     *
     * @param externalId
     * @param msisdn
     * @return
     * @throws org.airtel.ug.mypk.exceptions.TransactionStatusException
     */
    public final MobiquityReponseHandler inquireTransactionStatusOfExtId(String externalId, String msisdn) throws TransactionStatusException {

        OutputStream output = null;
        BufferedReader reader = null;
        InitialContext ic = null;

        try {

            ic = new InitialContext();

            String AM_IP_PORT = (String) ic.lookup("resource/am/socket");
            String MBQT_USERNAME = (String) ic.lookup("resource/am/user");
            String MBQT_PASSWORD = (String) ic.lookup("resource/am/pass");

            String request = "<COMMAND>\n"
                    + "    <TYPE>TXNEQREQ</TYPE>\n"
                    + "    <serviceType>TXNEQREQ</serviceType>\n"
                    + "    <interfaceId>TXNCORE</interfaceId>\n"
                    + "    <IS_TRANS_UNIQUE_CHECK_REQUIRED>Y</IS_TRANS_UNIQUE_CHECK_REQUIRED>\n"
                    + "    <EXTTRID>" + externalId + "</EXTTRID>\n"
                    + "    <USERNAME>" + MBQT_USERNAME + "</USERNAME>\n"
                    + "    <PASSWORD>" + MBQT_PASSWORD + "</PASSWORD>\n"
                    + "</COMMAND>";

            String charset = "UTF-8";

            URLConnection connection = new URL(AM_IP_PORT).openConnection();
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

            LOGGER.log(Level.INFO, "AM_RESPONSE: {0}", xmlresponse.toString() + " | " + msisdn);

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            MobiquityReponseHandler mobiquityReponseHandler = new MobiquityReponseHandler();
            saxParser.parse((new InputSource(new StringReader(xmlresponse.toString()))), mobiquityReponseHandler);

            LOGGER.log(Level.INFO, "AM_RESPONSE_TXNSTATUS: {0}", mobiquityReponseHandler.getTxnstatus() + " | " + msisdn);
            LOGGER.log(Level.INFO, "AM_RESPONSE_MESSAGE: {0}", mobiquityReponseHandler.getMessage() + " | " + msisdn);
            LOGGER.log(Level.INFO, "AM_RESPONSE_TXID: {0}", mobiquityReponseHandler.getTxnid() + " | " + msisdn);

            transactionLog.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
            transactionLog.setMobiquity_desc(mobiquityReponseHandler.getMessage());
            transactionLog.setMobiquity_transid(mobiquityReponseHandler.getTxnid());

            return mobiquityReponseHandler;
        } catch (IOException | NamingException | ParserConfigurationException | SAXException ex) {
            throw new TransactionStatusException("MOBIQUITY: " + ex.getLocalizedMessage());
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
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }
    
    
    
}
