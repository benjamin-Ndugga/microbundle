package org.ibm.micro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author benjamin
 */
public class SMSClient implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(SMSClient.class.getName());

    /**
     * *
     * Sends SMS from the Application to the Client
     *
     * @param reciever the number to recieve the SMS
     * @param message the message content
     */
    public static void send_sms(String reciever, String message) {
        BufferedReader in = null;
        try {
            InitialContext ic = new InitialContext();
            String smsip = (String) ic.lookup("resource/smsgwip");
            String smsport = (String) ic.lookup("resource/smsgwport");
            String smsclass = (String) ic.lookup("resource/mclass");
            String smssender = (String) ic.lookup("resource/smssender");

            message = URLEncoder.encode(message, "UTF-8");

            //message = message.replaceAll("\\ ", "+");
            URL url = new URL("http://" + smsip + ":" + smsport + "/cgi-bin/sendsms?username=tester&password=foobar&to=" + reciever + "&from=" + smssender + "&text=" + message + "&mclass=" + smsclass);
            in = new BufferedReader(new InputStreamReader(url.openStream()));
            String inputLine;

            String result = "";

            while ((inputLine = in.readLine()) != null) {
                result = inputLine;
            }
            in.close();

            LOGGER.log(Level.INFO, "SENDING SMS RESULT MSG {0} | {1}", new Object[]{result, reciever});

        } catch (NamingException | IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
                }
            }
        }
    }

}
