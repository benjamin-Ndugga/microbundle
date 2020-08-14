package org.airtel.ug.mypk.pojo;

import java.io.Serializable;

/**
 * This holds data for requests that have failed on AM and need to be checked
 *
 * @author Benjamin E Ndugga
 */
public class MicroBundleRetryRequest implements Serializable {

    private String msisdn;
    private String sessionId;
    private String sourceIp;
    private String externalId;
    private String imsi;
    private int bandId;
    private int optionId;
    private int currentRetryCount = 1;
    private MenuItem menuItem;

    public int getBandId() {
        return bandId;
    }

    public void setBandId(int bandId) {
        this.bandId = bandId;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public MicroBundleRetryRequest() {
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
        return "MicroBundleRetryRequest{" + "msisdn=" + msisdn + ", sessionId=" + sessionId + ", sourceIp=" + sourceIp + ", externalId=" + externalId + ", imsi=" + imsi + ", bandId=" + bandId + ", optionId=" + optionId + ", currentRetryCount=" + currentRetryCount + ", menuItem=" + menuItem + '}';
    }

}
