package org.airtel.ug.mypk.retry;

import java.io.Serializable;

/**
 * This holds data for requests that have failed on AM and need to be checked
 *
 * @author Benjamin E Ndugga
 */
public class RetryRequest implements Serializable {

    private String msisdn, sessionId, sourceIp, externalId, imsi;
    private int optionId, currentRetryCount = 1;

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public RetryRequest() {
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

    public int getOptionId() {
        return optionId;
    }

    public void setOptionId(int optionId) {
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
        return "RetryRequest{" + "msisdn=" + msisdn + ", sessionId=" + sessionId + ", sourceIp=" + sourceIp + ", externalId=" + externalId + ", imsi=" + imsi + ", optionId=" + optionId + ", currentRetryCount=" + currentRetryCount + '}';
    }

    

}
