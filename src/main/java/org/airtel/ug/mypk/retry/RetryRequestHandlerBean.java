package org.airtel.ug.mypk.retry;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.EJBException;
import javax.ejb.Schedule;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.naming.NamingException;
import org.airtel.ug.mypk.util.HzClient;

/**
 *
 * @author Benjamin E Ndugga
 */
//@Startup
//@Singleton
public class RetryRequestHandlerBean {

    private static final Logger LOGGER = Logger.getLogger("MYPK_EJB");
    private static final int MAX_ENTRIES = 10;

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Schedule(second = "*/5", minute = "*", hour = "*", info = "retry_request_processor", persistent = false)
    public void run() {
        for (int i = 1; i <= MAX_ENTRIES; i++) {
            LOGGER.log(Level.INFO, "CALL {0}", i);
            process();
        }
    }

    public void process() {
        try {

            LOGGER.log(Level.INFO, "CHECKING-PENDING-REQUESTS AT {0}", new Date());

            HzClient hzClient = new HzClient();

            Object pendingRequest = hzClient.fetchPendingRequest();

            if (pendingRequest == null) {

                LOGGER.log(Level.INFO, "NO-PENDING-REQUESTS.");

                return;
            }

            if (pendingRequest instanceof RetryRequest) {

                LOGGER.log(Level.INFO, "PROCESSING-PRE-PAID-RETRY-REQUEST");

                RetryRequest retryRequest = (RetryRequest) pendingRequest;

                int currentRetryCount = retryRequest.getCurrentRetryCount();

                LOGGER.log(Level.INFO, "CURRENT-RETRY-COUNT: {0} | {1}", new Object[]{currentRetryCount, retryRequest.getMsisdn()});

                mes.submit(new RetryProcessor(retryRequest));

            } else {
                LOGGER.log(Level.WARNING, "FAILED-TO-PARSE-OBJECT-FROM-QUEUE | {0}", pendingRequest);
            }

        } catch (IllegalStateException | NamingException | ClassCastException | EJBException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

}//end of class
