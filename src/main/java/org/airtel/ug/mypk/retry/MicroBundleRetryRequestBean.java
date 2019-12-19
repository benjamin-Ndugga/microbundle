package org.airtel.ug.mypk.retry;

import com.hazelcast.core.HazelcastInstance;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import org.airtel.ug.mypk.controllers.MicroBundleRequestProcessor;

/**
 *
 * @author Benjamin E Ndugga
 */
@Startup
@Singleton
public class MicroBundleRetryRequestBean {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleRetryRequestBean.class.getName());

    @Inject
    private HazelcastInstance client;

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Schedule(second = "0", minute = "*/1", hour = "*", info = "micro_bundle_retry", persistent = false)
    public void process() {
        try {
            
            LOGGER.log(Level.INFO, "CONNECTED-TO >>> | {0}", client.getName());
            

            LOGGER.log(Level.INFO, "CHECKING-PENDING-REQUESTS-AT {0}", new Date());

            List<MicroBundleRetryRequest> retryRequests = new MicroBundleRetryRequestFileHandler().readRetryTransactions();

            if (retryRequests.isEmpty()) {

                LOGGER.log(Level.INFO, "NO-PENDING-REQUESTS.");

                return;
            }

            retryRequests.forEach((retryRequest) -> {
                LOGGER.log(Level.INFO, "SUMBITTING >>> {0}", retryRequest);
                mes.submit(new MicroBundleRequestProcessor(retryRequest, client));
            });

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

}//end of class
