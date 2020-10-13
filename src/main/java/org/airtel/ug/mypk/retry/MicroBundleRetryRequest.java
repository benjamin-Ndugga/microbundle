package org.airtel.ug.mypk.retry;

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
    private int optionId;
    private int band_id;
    private String ocsProdId;
    private String amProdId;
    private int price;
    private int currentRetryCount = 1;

    private String mobiquity_code;
    private String mobiquity_desc;
    private String mobiquity_transid;
    private String mobiquity_xml_resp;
    private String processing_node;
    private String bundleName;

    private long file_age;

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getOcsProdId() {
        return ocsProdId;
    }

    public void setOcsProdId(String ocsProdId) {
        this.ocsProdId = ocsProdId;
    }

    public String getAmProdId() {
        return amProdId;
    }

    public void setAmProdId(String amProdId) {
        this.amProdId = amProdId;
    }

    public long getFile_age() {
        return file_age;
    }

    public void setFile_age(long file_age) {
        this.file_age = file_age;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public int getBand_id() {
        return band_id;
    }

    public void setBand_id(int band_id) {
        this.band_id = band_id;
    }

    public String getMobiquity_code() {
        return mobiquity_code;
    }

    public void setMobiquity_code(String mobiquity_code) {
        this.mobiquity_code = mobiquity_code;
    }

    public String getMobiquity_desc() {
        return mobiquity_desc;
    }

    public void setMobiquity_desc(String mobiquity_desc) {
        this.mobiquity_desc = mobiquity_desc;
    }

    public String getMobiquity_transid() {
        return mobiquity_transid;
    }

    public void setMobiquity_transid(String mobiquity_transid) {
        this.mobiquity_transid = mobiquity_transid;
    }

    public String getMobiquity_xml_resp() {
        return mobiquity_xml_resp;
    }

    public void setMobiquity_xml_resp(String mobiquity_xml_resp) {
        this.mobiquity_xml_resp = mobiquity_xml_resp;
    }

    public String getProcessing_node() {
        return processing_node;
    }

    public void setProcessing_node(String processing_node) {
        this.processing_node = processing_node;
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
        return "MicroBundleRetryRequest{" + "msisdn=" + msisdn + ", sessionId=" + sessionId + ", sourceIp=" + sourceIp + ", externalId=" + externalId + ", imsi=" + imsi + ", optionId=" + optionId + ", band_id=" + band_id + ", ocsProdId=" + ocsProdId + ", amProdId=" + amProdId + ", price=" + price + ", currentRetryCount=" + currentRetryCount + ", mobiquity_code=" + mobiquity_code + ", mobiquity_desc=" + mobiquity_desc + ", mobiquity_transid=" + mobiquity_transid + ", mobiquity_xml_resp=" + mobiquity_xml_resp + ", processing_node=" + processing_node + ", bundleName=" + bundleName + ", file_age=" + file_age + '}';
    }

}
