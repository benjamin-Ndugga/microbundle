package org.airtel.ug.hz;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author benjamin
 */
public class HZClient {

    private static final Logger LOGGER = Logger.getLogger("MYPAKALAST_HZ");
    private static final int DEFAULT_BAND = 6;
    private static final String BILLING_OPTION_MAP_NAME = "pnp.billingoption",
            MY_PAKALAST_MAP_NAME = "com.airtel.ug.micro", OPTION_ID_MAP_NAME = "pnp.optionid";

    /**
     * returns the band attached to a subscriber
     *
     * @param msisdn subscriber's number
     * @return
     */
    public Integer getBand(String msisdn) {

        HazelcastInstance client = null;
        try {

            //connect to the hazlecast IMDG
            client = connectToHzInstance();

            //get the map for imsis
            IMap<String, Integer> map = client.getMap(MY_PAKALAST_MAP_NAME);

            Integer band_found = map.get(msisdn);

            LOGGER.log(Level.INFO, "BAND-FOUND-FROM-HZ-INSTANCE {0} | {1}", new Object[]{band_found, msisdn});

            if (band_found == null) {

                LOGGER.log(Level.INFO, "DEFAULT_TO BAND " + DEFAULT_BAND + "| {0}", msisdn);

                return DEFAULT_BAND;
            } else {
                return band_found;
            }

        } catch (NullPointerException | IllegalStateException | NamingException ex) {

            LOGGER.log(Level.INFO, "DEFAULTING TO BAND {0} | {1}", new Object[]{DEFAULT_BAND, msisdn});

            LOGGER.log(Level.WARNING, "ERROR-ON-GETTING-BAND" + ex.getLocalizedMessage() + " | " + msisdn, ex);

            return DEFAULT_BAND;
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * return the option id selected by the subscriber
     *
     * @param msisdn the requesting subscriber's number
     * @return
     * @throws NullPointerException
     * @throws IllegalStateException
     * @throws NamingException
     */
    public Integer getOptionId(String msisdn) throws NullPointerException, IllegalStateException, NamingException {
        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> map = client.getMap(OPTION_ID_MAP_NAME);

            return map.get(msisdn);

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * saves the option id selected by the subscriber
     *
     * @param msisdn the subscriber that has selected this option id
     * @param optionId the option id selected
     * @throws NamingException
     * @throws NullPointerException
     * @throws IllegalStateException
     */
    public void saveOptionId(String msisdn, int optionId) throws NamingException, NullPointerException, IllegalStateException {
        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> map = client.getMap(OPTION_ID_MAP_NAME);

            map.put(msisdn, optionId);

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * fetched the saved billing option for the subscriber
     *
     * @param msisdn the subscriber that selected the billing option
     * @return
     * @throws IllegalStateException
     * @throws NamingException
     */
    public Integer getBillingOption(String msisdn) throws IllegalStateException, NamingException {

        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the billing option
            IMap<String, Integer> map = client.getMap(BILLING_OPTION_MAP_NAME);

            return map.get(msisdn);

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * saves the billing option that's been selected
     *
     * @param msisdn the subscriber's number that has saved the billing option
     * @param billingOption the billing option that has been selected
     * @throws IllegalStateException
     * @throws NamingException
     */
    public void saveBillingOption(String msisdn, String billingOption) throws IllegalStateException, NamingException {
        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> map = client.getMap(BILLING_OPTION_MAP_NAME);

            map.put(msisdn, Integer.parseInt(billingOption));

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * clears the session data for the subscriber
     *
     * @param msisdn
     */
    public void clearSessionData(String msisdn) {

        LOGGER.log(Level.INFO, "CLEAR_SESSION_DATA | {0}", msisdn);

        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> mapOptionId = client.getMap(OPTION_ID_MAP_NAME);
            mapOptionId.remove(msisdn);

            IMap<String, Integer> mapBillingOption = client.getMap(BILLING_OPTION_MAP_NAME);
            mapBillingOption.remove(msisdn);

        } catch (IllegalStateException | NamingException ex) {
            Logger.getLogger(HZClient.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * Creates a smart client that connects to the HZ-Instance
     *
     * @return
     * @throws IllegalStateException
     * @throws NamingException
     */
    private HazelcastInstance connectToHzInstance() throws IllegalStateException, NamingException {

        InitialContext ic = new InitialContext();

        String hz_name = (String) ic.lookup("resource/hz/name");
        String hz_pass = (String) ic.lookup("resource/hz/pass");

        LOGGER.log(Level.INFO, "HZ_NAME {0}", hz_name);
        LOGGER.log(Level.INFO, "HZ_PASS {0}", hz_pass);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setProperty("hazelcast.logging.type", "none");

        clientConfig.setGroupConfig(new GroupConfig(hz_name, hz_pass));
        ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();

        LOGGER.log(Level.INFO, "CONNECTING TO HZ-INSTANCE");

        String hz_ip_list = (String) ic.lookup("resource/hz/ip");
        String[] ip_list = hz_ip_list.split("\\,");

        for (String ip : ip_list) {
            LOGGER.log(Level.INFO, "ADDING IP {0}", ip);
            networkConfig.addAddress(ip);
        }

        networkConfig.setSmartRouting(true);
        networkConfig.setConnectionTimeout(1000);
        networkConfig.setConnectionAttemptPeriod(250);
        networkConfig.setConnectionAttemptLimit(1);
        clientConfig.setNetworkConfig(networkConfig);

        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);

        LOGGER.log(Level.INFO, "CONNECTED TO | {0}", client.getName());

        return client;
    }

}
