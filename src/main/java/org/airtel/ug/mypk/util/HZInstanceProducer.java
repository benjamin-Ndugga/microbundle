package org.airtel.ug.mypk.util;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

/**
 *
 * @author Benjamin E Ndugga
 */
public class HZInstanceProducer {

    private static final Logger LOGGER = Logger.getLogger(HZInstanceProducer.class.getName());

    @Produces
    @Singleton
    public HazelcastInstance createHazelcastInstance() {

        LOGGER.log(Level.INFO, "INIT-TO-HZ-INSTANCE");

        return HazelcastClient.newHazelcastClient();
    }

//    @Produces
//    @Singleton
//    public HazelcastInstance connectToHzInstance() {
//
//        InitialContext ic = null;
//
//        try {
//
//            ic = new InitialContext();
//
//            ClientConfig clientConfig = new ClientConfig();
//            clientConfig.setProperty("hazelcast.logging.type", "jdk");
//
//            String hz_name = (String) ic.lookup("resource/hz/name");
//            String hz_pass = (String) ic.lookup("resource/hz/pass");
//
//            LOGGER.log(Level.CONFIG, "HZ_NAME {0}", hz_name);
//            LOGGER.log(Level.CONFIG, "HZ_PASS {0}", hz_pass);
//
//            clientConfig.setGroupConfig(new GroupConfig(hz_name, hz_pass));
//            ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();
//
//            LOGGER.log(Level.CONFIG, "INIT-TO-HZ-INSTANCE");
//
//            String hz_ip_list = (String) ic.lookup("resource/hz/ip");
//            String[] ip_list = hz_ip_list.split("\\,");
//
//            for (String ip : ip_list) {
//                LOGGER.log(Level.CONFIG, "ADDING-IP {0}", ip);
//                networkConfig.addAddress(ip);
//            }
//
//            networkConfig.setSmartRouting(true);
//            networkConfig.setConnectionTimeout(1000);
//            networkConfig.setConnectionAttemptPeriod(2000);
//            networkConfig.setConnectionAttemptLimit(1);
//            clientConfig.setNetworkConfig(networkConfig);
//
//            return HazelcastClient.newHazelcastClient(clientConfig);
//
//        } catch (NamingException ex) {
//            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
//            return null;
//        } finally {
//            try {
//                if (ic != null) {
//                    ic.close();
//                }
//            } catch (NamingException ex) {
//                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
//            }
//        }
//    }
}
