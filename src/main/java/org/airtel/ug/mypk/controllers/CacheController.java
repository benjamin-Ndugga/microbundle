package org.airtel.ug.mypk.controllers;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;

import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 *
 * @author Benjamin E Ndugga
 */
@ApplicationScoped
public class CacheController {

    private static final Logger LOGGER = Logger.getLogger(CacheController.class.getName());

    private static final int DEFAULT_BAND = 10;
    private static final String MICRO_BUNDLE_MAP_NAME = "pnp.mypakalast";
    private static final String OPTION_ID_MAP_NAME = "mypk.optionid";
    private static final String BILLING_OPTION_MAP_NAME = "mypk.billingoption";

    private static HazelcastInstance HAZELCAST_INSTANCE;

    @PostConstruct
    public void createHazelcastInstance() {

        LOGGER.log(Level.INFO, "initialise connection to cache server...");

        HAZELCAST_INSTANCE = HazelcastClient.newHazelcastClient();
    }

    @PreDestroy
    public void close() {

        LOGGER.log(Level.INFO, "shutdown connection on cache server...");

        HAZELCAST_INSTANCE.shutdown();
    }

    public void flushCache() {

        try {

            LOGGER.log(Level.INFO, "FLUSHING-CACHE: " + MICRO_BUNDLE_MAP_NAME);

            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(MICRO_BUNDLE_MAP_NAME);

            map.flush();

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

    /**
     *
     * @param msisdn
     * @param band
     * @return
     */
    public Integer addNumberToCache(String msisdn, String band) {

        try {

            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(MICRO_BUNDLE_MAP_NAME);

            return map.put(msisdn, Integer.parseInt(band));

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            return null;
        }

    }

    /**
     *
     * @param msisdn
     * @return
     */
    public Integer flushNumberFromCache(String msisdn) {
        Integer band_found = null;

        try {

            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(MICRO_BUNDLE_MAP_NAME);

            band_found = map.remove(msisdn);

            LOGGER.log(Level.INFO, "BAND-FOUND-FROM HZ-INSTANCE {0} | {1}", new Object[]{band_found, msisdn});

            return band_found;

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, "ERROR-ON-GETTING-BAND: " + ex.getLocalizedMessage() + " | " + msisdn, ex);

            return band_found;
        }
    }

    /**
     * return the band that belongs to the subscriber
     *
     * @param msisdn the requesting subscriber
     * @return
     */
    public Integer getBand(String msisdn) {

        //HazelcastInstance client = null;
        try {

            //connect to the hazlecast IMDG
            //client = connectToHzInstance();
            //get the map for imsis
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(MICRO_BUNDLE_MAP_NAME);

            Integer band_found = map.get(msisdn);

            LOGGER.log(Level.INFO, "BAND-FOUND FROM HZ-INSTANCE {0} | {1}", new Object[]{band_found, msisdn});

            if (band_found == null) {

                LOGGER.log(Level.INFO, "DEFAULT_TO BAND " + DEFAULT_BAND + "| {0}", msisdn);

                return DEFAULT_BAND;
            } else {
                return band_found;
            }

        } catch (Exception ex) {

            LOGGER.log(Level.INFO, "DEFAULTING TO BAND {0} | {1}", new Object[]{DEFAULT_BAND, msisdn});

            LOGGER.log(Level.WARNING, "ERROR-ON-GETTING-BAND: " + ex.getLocalizedMessage() + " | " + msisdn, ex);

            return DEFAULT_BAND;
        }
    }

    /**
     *
     * @param sessionId the requesting msisdn
     * @return the option selected from the menu
     * @throws NullPointerException
     * @throws IllegalStateException
     */
    public Integer getOptionId(String sessionId) {

        try {

            //LOGGER.log(Level.INFO, "CONNECT-TO-HZ-INSTANCE | {0}", sessionId);
            //client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(OPTION_ID_MAP_NAME);

            Integer i = map.get(sessionId);

            LOGGER.log(Level.INFO, "OPTION-ID-VALUE-FOUND: {0} | {1}", new Object[]{i, sessionId});

            return i;

        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     *
     * @param sessionId
     * @param optionId
     * @throws NullPointerException
     * @throws IllegalStateException
     */
    public void saveOptionId(String sessionId, int optionId) {

        try {

            LOGGER.log(Level.INFO, "SAVING-OPTION-ID: {0} | {1}", new Object[]{optionId, sessionId});

            //client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(OPTION_ID_MAP_NAME);

            map.put(sessionId, optionId);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        }
    }

    /**
     *
     * @param sessionId
     * @param optionId
     * @throws NullPointerException
     * @throws IllegalStateException
     */
    public void saveOptionIdAsync(String sessionId, int optionId) {

        InitialContext ctx = null;
        try {
            ctx = new InitialContext();
            ManagedExecutorService mes = (ManagedExecutorService) ctx.lookup("concurrent/mypakalast");

            mes.submit(() -> {

                try {

                    LOGGER.log(Level.INFO, "SAVING-OPTION-ID: {0} | {1}", new Object[]{optionId, sessionId});

                    //client = connectToHzInstance();
                    //get the option id
                    IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(OPTION_ID_MAP_NAME);

                    map.put(sessionId, optionId);

                } catch (Exception ex) {

                    LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

                }
            });
        } catch (NamingException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } catch (NamingException ex) {
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }

    /**
     * returns the billing option for the subscriber
     *
     * @param msisdn the requesting subscriber
     * @return
     * @throws IllegalStateException
     */
    public Integer getBillingOption(String msisdn) {

        try {

            LOGGER.log(Level.INFO, "CHECK-BILLING-OPTION | {0}", msisdn);

            //client = connectToHzInstance();
            //get the billing option
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(BILLING_OPTION_MAP_NAME);

            Integer i = map.get(msisdn);

            LOGGER.log(Level.INFO, "BILLING-OPTION-FOUND: {0} | {1}", new Object[]{i, msisdn});

            return i;

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            return null;
        }
    }

    /**
     *
     * @param sessionId
     * @param billingOption
     * @throws IllegalStateException
     */
    public void saveBillingOption(String sessionId, int billingOption) {

        try {

            LOGGER.log(Level.INFO, "SAVE-BILLING-OPTION: {0} | {1}", new Object[]{billingOption, sessionId});

            //client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(BILLING_OPTION_MAP_NAME);

            map.put(sessionId, billingOption);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        }
    }

    /**
     *
     * @param sessionId
     * @param billingOption
     */
    public void saveBillingOptionAsync(String sessionId, int billingOption) {

        LOGGER.log(Level.INFO, "SAVE-BILLING-OPTION-ANSYNC: {0} | {1}", new Object[]{billingOption, sessionId});

        InitialContext ctx = null;
        try {
            ctx = new InitialContext();
            ManagedExecutorService mes = (ManagedExecutorService) ctx.lookup("concurrent/mypakalast");

            mes.submit(() -> {
                try {

                    //client = connectToHzInstance();
                    //get the option id
                    IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(BILLING_OPTION_MAP_NAME);

                    map.put(sessionId, billingOption);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
                }
            });

        } catch (NamingException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
                }
            }
        }
    }

    /**
     *
     *
     * clears session data for the requesting customer
     *
     * @param sessionId the requesting customer
     */
    public void clearSessionData(String sessionId) {

        LOGGER.log(Level.INFO, "CLEAR-SESSION-DATA | {0}", sessionId);

        try {
            //client = connectToHzInstance();
            //get the option id
            IMap<String, Integer> mapOptionId = HAZELCAST_INSTANCE.getMap(OPTION_ID_MAP_NAME);
            Integer foundOptionId = mapOptionId.remove(sessionId);
            LOGGER.log(Level.INFO, "REMOVE-OPTION-ID: {0} | {1}", new Object[]{foundOptionId, sessionId});

            IMap<String, Integer> mapBillingOption = HAZELCAST_INSTANCE.getMap(BILLING_OPTION_MAP_NAME);
            Integer foundBillingOption = mapBillingOption.remove(sessionId);
            LOGGER.log(Level.INFO, "REMOVE-BILLING-OPTION: {0} | {1}", new Object[]{foundBillingOption, sessionId});

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

}//end of class
