package org.airtel.ug.mypk.retry;

import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.airtel.ug.mypk.controllers.MicroBundleRequestProcessor;

/**
 *
 * @author Benjamin E Ndugga
 */
@Startup
@Singleton
public class RetryRequestBean {

    private static final Logger LOGGER = Logger.getLogger("MYPK_EJB");

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Schedule(second = "0", minute = "*/1", hour = "*", info = "retry_request_processor", persistent = false)
    public void process() {
        try {

            LOGGER.log(Level.INFO, "CHECKING-PENDING-REQUESTS-AT {0}", new Date());

            List<RetryRequest> retryRequests = new RetryRequestFileHandler().readRetryTransactions();

            if (retryRequests.isEmpty()) {

                LOGGER.log(Level.INFO, "NO-PENDING-REQUESTS.");

                return;
            }

            retryRequests.forEach((retryRequest) -> {
                LOGGER.log(Level.INFO, "SUMBITTING >>> {0}", retryRequest);
                mes.submit(new MicroBundleRequestProcessor(retryRequest));
            });

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

}//end of class
