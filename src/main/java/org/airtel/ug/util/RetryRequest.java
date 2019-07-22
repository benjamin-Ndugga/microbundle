package org.airtel.ug.util;

import java.io.Serializable;

/**
 * This holds data for requests that have failed on AM and need to be checked
 *
 * @author Benjamin E Ndugga
 */
public class RetryRequest implements Serializable {

    private String msisdn, optionId, sessionId, sourceIp, externalId;
    private int currentRetryCount = 5;

    public RetryRequest() {
    }

    public RetryRequest(String msisdn, String bnumber, String optionId, String sessionId, String externalId, String sourceIp) {
        this.msisdn = msisdn;
        this.optionId = optionId;
        this.sessionId = sessionId;
        this.sourceIp = sourceIp;
        this.externalId = externalId;
    }

    public int getCurrentRetryCount() {
        return currentRetryCount;
    }

    public void setCurrentRetryCount(int currentRetryCount) {
        this.currentRetryCount = currentRetryCount;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public String toString() {
        return "MYPAKA-TXN {" + msisdn + "," + optionId + "," + sessionId + "| RETRY-COUNT :" + currentRetryCount + "}";
    }

}
