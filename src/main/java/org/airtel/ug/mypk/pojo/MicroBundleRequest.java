package org.airtel.ug.mypk.pojo;

/**
 *
 * @author Benjamin E Ndugga
 */
public class MicroBundleRequest {

    private String msisdn;
    private String imsi;
    private String sessionId;
    private String sourceIp;
    private String pin;
    private int bandId;
    private int optionId;
    private String serviceClass;

    public String getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
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

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public int getBandId() {
        return bandId;
    }

    public void setBandId(int bandId) {
        this.bandId = bandId;
    }

    public int getOptionId() {
        return optionId;
    }

    public void setOptionId(int optionId) {
        this.optionId = optionId;
    }

    //(pin != null ? pin.replaceAll("\\S", "x") : pin)
    @Override
    public String toString() {
        return "MicroBundleRequest{" + "msisdn=" + msisdn + ", imsi=" + imsi + ", sessionId=" + sessionId + ", sourceIp=" + sourceIp + ", pin=" + (pin != null ? pin.replaceAll("\\S", "x") : pin) + ", bandId=" + bandId + ", optionId=" + optionId + ", serviceClass=" + serviceClass + '}';
    }

}
