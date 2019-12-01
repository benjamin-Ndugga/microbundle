package org.airtel.ug.mypk.util;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author benjamin
 */
public class HzClient {

    private static final Logger LOGGER = Logger.getLogger("MYPK_HZ");
    private static final int DEFAULT_BAND = 10;
    //private static final String MICRO_BUNDLE_MAP_NAME = "com.airtel.ug.micro";
    private static final String MICRO_BUNDLE_MAP_NAME = "pnp.mypakalast";
    private static final String OPTION_ID_MAP_NAME = " pnp.optionid";
    private static final String BILLING_OPTION_MAP_NAME = "pnp.billingoption";
    //private static final String AM_RETRY_QUEUE_NAME = "am.mypk";

    /**
     * return the band that belongs to the subscriber
     *
     * @param msisdn the requesting subscriber
     * @return
     */
    public Integer getBand(String msisdn) {

        HazelcastInstance client = null;
        try {

            //connect to the hazlecast IMDG
            client = connectToHzInstance();

            //get the map for imsis
            IMap<String, Integer> map = client.getMap(MICRO_BUNDLE_MAP_NAME);

            Integer band_found = map.get(msisdn);

            LOGGER.log(Level.INFO, "BAND-FOUND FROM HZ-INSTANCE {0} | {1}", new Object[]{band_found, msisdn});

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
     *
     * @param sessionId the requesting msisdn
     * @return the option selected from the menu
     * @throws NullPointerException
     * @throws IllegalStateException
     * @throws NamingException
     */
    public Integer getOptionId(String sessionId) throws NullPointerException, IllegalStateException, NamingException {
        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> map = client.getMap(OPTION_ID_MAP_NAME);

            Integer i = map.get(sessionId);

            LOGGER.log(Level.INFO, "OPTION-ID-VALUE-FOUND: {0} | {1}", new Object[]{i, sessionId});

            if (i == null) {
                return null;
            } else {
                return i;
            }

        } catch (NullPointerException ex) {
            return null;
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     *
     * @param sessionId
     * @param optionId
     * @throws NamingException
     * @throws NullPointerException
     * @throws IllegalStateException
     */
    public void saveOptionId(String sessionId, int optionId) throws NamingException, NullPointerException, IllegalStateException {
        HazelcastInstance client = null;
        try {

            LOGGER.log(Level.INFO, "SAVING-OPTION-ID: {0} | {1}", new Object[]{optionId, sessionId});

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> map = client.getMap(OPTION_ID_MAP_NAME);

            map.put(sessionId, optionId);

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * returns the billing option for the subscriber
     *
     * @param msisdn the requesting subscriber
     * @return
     * @throws IllegalStateException
     * @throws NamingException
     */
    public Integer getBillingOption(String msisdn) throws IllegalStateException, NamingException {

        HazelcastInstance client = null;
        try {

            LOGGER.log(Level.INFO, "CHECK-BILLING-OPTION | {0}", msisdn);

            client = connectToHzInstance();

            //get the billing option
            IMap<String, Integer> map = client.getMap(BILLING_OPTION_MAP_NAME);

            Integer i = map.get(msisdn);

            LOGGER.log(Level.INFO, "BILLING-OPTION-FOUND: {0} | {1}", new Object[]{i, msisdn});

            if (i == null) {
                return null;
            } else {
                return i;
            }

        } catch (NullPointerException ex) {
            return null;
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     *
     * @param sessionId
     * @param billingOption
     * @throws IllegalStateException
     * @throws NamingException
     */
    public void saveBillingOption(String sessionId, int billingOption) throws IllegalStateException, NamingException {
        HazelcastInstance client = null;
        try {

            LOGGER.log(Level.INFO, "SAVE-BILLING-OPTION: {0} | {1}", new Object[]{billingOption, sessionId});

            client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> map = client.getMap(BILLING_OPTION_MAP_NAME);

            map.put(sessionId, billingOption);

        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    /**
     * clears session data for the requesting customer
     *
     * @param sessionId the requesting customer
     */
    public void clearSessionData(String sessionId) {

        LOGGER.log(Level.INFO, "CLEAR_SESSION_DATA | {0}", sessionId);

        HazelcastInstance client = null;
        try {

            client = connectToHzInstance();

            //get the option id
            IMap<String, Integer> mapOptionId = client.getMap(OPTION_ID_MAP_NAME);
            Integer foundOptionId = mapOptionId.remove(sessionId);
            LOGGER.log(Level.INFO, "REMOVE-OPTION-ID: {0} | {1}", new Object[]{foundOptionId, sessionId});

            IMap<String, Integer> mapBillingOption = client.getMap(BILLING_OPTION_MAP_NAME);
            Integer foundBillingOption = mapBillingOption.remove(sessionId);
            LOGGER.log(Level.INFO, "REMOVE-BILLING-OPTION: {0} | {1}", new Object[]{foundBillingOption, sessionId});

        } catch (IllegalStateException | NamingException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }

    }

    /**
     * poll a request from the queue
     *
     * @param <T> return either a post-paid request or prepaid
     * @return
     * @throws IllegalStateException
     * @throws NamingException
     */
//    public <T> T fetchPendingRequest() throws IllegalStateException, NamingException {
//        HazelcastInstance client = null;
//        try {
//            //connect to HzInstance
//            client = connectToHzInstance();
//
//            IQueue<T> queue = client.getQueue(AM_RETRY_QUEUE_NAME);
//
//            return queue.poll();
//
//        } finally {
//            if (client != null) {
//                client.shutdown();
//            }
//        }
//    }
    /**
     *
     * @param <T> takes any type i.e Pre-Paid or Post-Paid object
     * @param request the request to be added to the queue
     * @throws IllegalStateException in-case the Hazelcast Instance is down
     */
//    public <T> void addRetryRequestToQueue(T request) {
//        HazelcastInstance client = null;
//        try {
//            //connect to HzInstance
//            client = connectToHzInstance();
//
//            IQueue<T> queue = client.getQueue(AM_RETRY_QUEUE_NAME);
//
//            LOGGER.log(Level.INFO, "ADDING-REQUESET-TO-QUEUE ");
//
//            queue.put(request);
//
//        } catch (IllegalStateException | NamingException | InterruptedException ex) {
//
//            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
//
//        } finally {
//            if (client != null) {
//                client.shutdown();
//            }
//        }
//    }
    private HazelcastInstance connectToHzInstance() throws IllegalStateException, NamingException {

        InitialContext ic = null;

        try {

            ic = new InitialContext();

            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setProperty("hazelcast.logging.type", "none");

            String hz_name = (String) ic.lookup("resource/hz/name");
            String hz_pass = (String) ic.lookup("resource/hz/pass");

            LOGGER.log(Level.CONFIG, "HZ_NAME {0}", hz_name);
            LOGGER.log(Level.CONFIG, "HZ_PASS {0}", hz_pass);

            clientConfig.setGroupConfig(new GroupConfig(hz_name, hz_pass));
            ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();

            LOGGER.log(Level.CONFIG, "CONNECTING-TO-HZ-INSTANCE");

            String hz_ip_list = (String) ic.lookup("resource/hz/ip");
            String[] ip_list = hz_ip_list.split("\\,");

            for (String ip : ip_list) {
                LOGGER.log(Level.CONFIG, "ADDING-IP {0}", ip);
                networkConfig.addAddress(ip);
            }

            networkConfig.setSmartRouting(true);
            networkConfig.setConnectionTimeout(1000);
            networkConfig.setConnectionAttemptPeriod(0);
            networkConfig.setConnectionAttemptLimit(1);
            clientConfig.setNetworkConfig(networkConfig);

            HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);

            LOGGER.log(Level.CONFIG, "CONNECTED-TO | {0}", client.getName());

            return client;
        } finally {
            if (ic != null) {
                ic.close();
            }
        }
    }

}
