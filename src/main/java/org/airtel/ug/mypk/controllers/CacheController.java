package org.airtel.ug.mypk.controllers;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.IntegrationEnquiryResult;
import com.huawei.www.bme.cbsinterface.cbs.businessmgr.IntegrationEnquiryResultSubscriberInfo;
import com.huawei.www.bme.cbsinterface.cbs.businessmgrmsg.IntegrationEnquiryResultMsg;
import com.huawei.www.bme.cbsinterface.common.ResultHeader;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.rpc.ServiceException;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.ibm.ws.OCSWebMethods;

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
    private static final String SERVICE_CLASS_MAP_NAME = "pnp.serviceclass";

    private static HazelcastInstance HAZELCAST_INSTANCE;

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Resource(lookup = "resource/ocs/ip")
    private String OCS_IP;

    @Resource(lookup = "resource/ocs/port")
    private String OCS_PORT;

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

    /**
     *
     * @param msisdn
     * @return
     * @throws MyPakalastBundleException
     */
    public String fetchServiceClass(String msisdn) throws MyPakalastBundleException {
        try {
            LOGGER.log(Level.INFO, "FETCH-CACHED-SERVICECLASS |{0}", msisdn);

            //get the option id
            IMap<String, String> map = HAZELCAST_INSTANCE.getMap(SERVICE_CLASS_MAP_NAME);

            String serviceClass = map.get(msisdn);

            LOGGER.log(Level.INFO, "CACHED-SERVICECLASS: {0} | {1}", new Object[]{serviceClass, msisdn});

            if (serviceClass == null) {

                LOGGER.log(Level.INFO, "LOADED-OCS_IP {0}", OCS_IP);
                LOGGER.log(Level.INFO, "LOADED-OCS_PORT {0}", OCS_PORT);

                OCSWebMethods ocsWebMethods = new OCSWebMethods(OCS_IP, OCS_PORT);

                LOGGER.log(Level.INFO, "QUERY-SERVICECLASS | {0}", msisdn);

                IntegrationEnquiryResultMsg integrationEnquiry = ocsWebMethods.integrationEnquiry(msisdn.substring(3), "0", "DSE");
                IntegrationEnquiryResult integrationEnquiryResult = integrationEnquiry.getIntegrationEnquiryResult();

                ResultHeader resultHeader = integrationEnquiry.getResultHeader();

                LOGGER.log(Level.INFO, "RESULT-CODE: {0} | {1}", new Object[]{resultHeader.getResultCode(), msisdn});
                LOGGER.log(Level.INFO, "RESULT-DESC: {0} | {1}", new Object[]{resultHeader.getResultDesc(), msisdn});

                if (integrationEnquiryResult != null) {

                    LOGGER.log(Level.INFO, "RESULT-OBJECT | {0}", integrationEnquiryResult);

                    IntegrationEnquiryResultSubscriberInfo subscriberInfo = integrationEnquiryResult.getSubscriberInfo();
                    String mainProductId = subscriberInfo.getSubscriber().getMainProductID();

                    LOGGER.log(Level.INFO, "OCS-SERVICECLASS: {0} | {1}", new Object[]{serviceClass, msisdn});

                    //cache this service class
                    mes.execute(() -> {
                        map.put(msisdn, mainProductId);
                    });
                    //we can not access the service class in a separate thread 
                    //as it's not declared as final we will need to use another variable
                    serviceClass = (mainProductId);
                } else {

                    LOGGER.log(Level.INFO, "FAILED-TO-QUERY-SERVICECLASS | {0}", msisdn);

                }
            }

            return serviceClass;

        } catch (RemoteException | ServiceException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            throw new MyPakalastBundleException("Failed to query subscriber service class.", 700);
        }
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
    public Integer fetchSubscriberBand(String msisdn) {

        //HazelcastInstance client = null;
        try {

            //connect to the hazlecast IMDG
            //client = connectToHzInstance();
            //get the map for imsis
            IMap<String, Integer> map = HAZELCAST_INSTANCE.getMap(MICRO_BUNDLE_MAP_NAME);

            Integer band_found = map.get(msisdn);

            if (band_found == null) {

                LOGGER.log(Level.INFO, "DEFAULT-TO-BAND " + DEFAULT_BAND + "| {0}", msisdn);

                return DEFAULT_BAND;
            } else {

                LOGGER.log(Level.INFO, "FOUND-BAND {0} | {1}", new Object[]{band_found, msisdn});

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
