package org.airtel.ug.mypk.retry;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJBException;
import javax.ejb.Schedule;
import javax.naming.NamingException;
import org.airtel.ug.mypk.util.HzClient;

/**
 *
 * @author Benjamin E Ndugga
 */

public class RetryRequestHandlerBean {

    public static final Logger LOGGER = Logger.getLogger("MYPK_EJB");

    @Schedule(second = "*/10", minute = "*", hour = "*", info = "retry_request_processor", persistent = false)
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

                new Thread(new RetryProcessor(retryRequest)).start();

            } else {
                LOGGER.log(Level.WARNING, "FAILED-TO-PARSE-OBJECT-FROM-QUEUE | {0}", pendingRequest);
            }

        } catch (IllegalStateException | NamingException | ClassCastException | EJBException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

}//end of class
