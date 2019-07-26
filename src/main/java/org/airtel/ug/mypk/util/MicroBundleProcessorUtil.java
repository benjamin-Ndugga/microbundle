package org.airtel.ug.mypk.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
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
import org.airtel.ug.mypk.am.MobiquityReponseHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Benjamin
 */
public class MicroBundleProcessorUtil {

    public static final Logger LOGGER = Logger.getLogger("MYPAKALAST");
    public static final String MOBIQUITY_SUCCESS_CODE = "200";
    public static final String OCS_SUCCESS_CODE = "405000000";

    public static final String OCS_OPERATOR_ID = "MicroBundle";

    public String OCS_IP, OCS_PORT;
    public final RequestLog requestLog = new RequestLog();
    public int MAX_RETRY_COUNT = 5;

    static {
        
        HostNameVerifier.verify();
        
    }

    public MicroBundleProcessorUtil() {
        InitialContext ctx = null;

        try {

            ctx = new InitialContext();
            OCS_IP = (String) ctx.lookup("resource/ocs/ip");
            OCS_PORT = (String) ctx.lookup("resource/ocs/port");

            requestLog.setChannel("USSD");

            //set the processing node
            requestLog.setProcessingNode(java.net.InetAddress.getLocalHost().getHostAddress());

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

            LOGGER.log(Level.INFO, "LOGGING_REQUEST | {0}", requestLog.getMsisdn());

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
                    + "CHANNEL) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

            statement.setString(1, requestLog.getMsisdn());
            statement.setString(2, requestLog.getImsi());
            statement.setString(3, requestLog.getSessionid());
            statement.setInt(4, requestLog.getBand_id());
            statement.setString(5, requestLog.getOcsResp());
            statement.setString(6, requestLog.getOcsDesc());
            statement.setString(7, requestLog.getRequestIp());
            statement.setString(8, requestLog.getProcessingNode());
            statement.setInt(9, requestLog.getOptionId());
            statement.setString(10, requestLog.getRequestSerial());
            statement.setString(11, requestLog.getOcsProdID());
            statement.setString(12, requestLog.getAmProdId());
            statement.setInt(13, requestLog.getPrice());
            statement.setString(14, requestLog.getMobiquity_code());
            statement.setString(15, requestLog.getMobiquity_desc());
            statement.setString(16, requestLog.getMobiquity_transid());
            statement.setString(17, requestLog.getExt_transid());
            statement.setString(18, requestLog.getMobiquity_xml_resp());
            statement.setString(19, requestLog.getException_str());
            statement.setString(20, requestLog.getChannel());

            int i = statement.executeUpdate();

            connection.commit();

            LOGGER.log(Level.INFO, "EXECUTE_UPDATE_RESPONSE {0} | {1}", new Object[]{i, requestLog.getMsisdn()});

        } catch (NullPointerException | NamingException | SQLException ex) {

            SMSClient.send_sms(requestLog.getMsisdn(), "Your request can not be processed at the moment!,Please try again later");
            LOGGER.log(Level.INFO, null, ex + " | " + requestLog.getMsisdn());

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
     * @throws MalformedURLException
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws NamingException
     */
    public final MobiquityReponseHandler debitMobiquityAccount(String msisdn, MenuItem menuItem, String pin, String externalId) throws MalformedURLException, IOException, ParserConfigurationException, NamingException {

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

            requestLog.setMobiquity_xml_resp(xmlresponse.toString());

            LOGGER.log(Level.INFO, "AM_RESPONSE: {0} | {1}", new Object[]{xmlresponse.toString(), msisdn});

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            MobiquityReponseHandler mobiquityReponseHandler = new MobiquityReponseHandler();
            saxParser.parse((new InputSource(new StringReader(xmlresponse.toString()))), mobiquityReponseHandler);

            LOGGER.log(Level.INFO, "TXNSTATUS {0} | {1}", new Object[]{mobiquityReponseHandler.getTxnstatus(), msisdn});
            LOGGER.log(Level.INFO, "MESSAGE  {0} | {1}", new Object[]{mobiquityReponseHandler.getMessage(), msisdn});

            requestLog.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
            requestLog.setMobiquity_desc(mobiquityReponseHandler.getMessage());
            requestLog.setMobiquity_transid(mobiquityReponseHandler.getTxnid());

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

    /**
     *
     * @return the internal session id for this request
     */
    public final String generateInternalSessionId() {
        //randomise the id
        char alphabet[] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
            'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
            'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
        Random random = new Random();

        String externalId = (alphabet[random.nextInt(alphabet.length)] + "" + random.nextInt(10000000));

        LOGGER.log(Level.INFO, "INTERNAL-SESSION-ID {0} ", new Object[]{externalId});

        return externalId;
    }

    /**
     *
     * @param externalId
     * @param msisdn
     * @return
     * @throws java.net.MalformedURLException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public final MobiquityReponseHandler inquireTransactionStatusOfExtId(String externalId, String msisdn) throws MalformedURLException, IOException, ParserConfigurationException, SAXException, NamingException {

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

            requestLog.setMobiquity_code(mobiquityReponseHandler.getTxnstatus());
            requestLog.setMobiquity_desc(mobiquityReponseHandler.getMessage());
            requestLog.setMobiquity_transid(mobiquityReponseHandler.getTxnid());

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

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }
}
