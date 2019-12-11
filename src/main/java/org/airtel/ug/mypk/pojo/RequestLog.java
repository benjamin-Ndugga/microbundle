package org.airtel.ug.mypk.pojo;

import java.io.Serializable;

/**
 *
 * @author benjamin
 */
public class RequestLog implements Serializable {

    private static final long serialVersionUID = 20181210L;

    private int id, optionId, band_id, price;
    private String msisdn, channel;
    private String imsi;
    private String sessionid;
    private String ocsResp, ocsDesc, requestSerial;
    private String requestIp;
    private String processingNode;
    private String ocsProdID, amProdId;

    private String mobiquity_xml_resp;
    private String mobiquity_transid;
    private String mobiquity_desc;
    private String mobiquity_code;
    private String ext_transid;
    private String exception_str;

    public String getException_str() {
        return exception_str;
    }

    public void setException_str(String exception_str) {
        this.exception_str = exception_str;
    }

    public String getExt_transid() {
        return ext_transid;
    }

    public void setExt_transid(String ext_transid) {
        this.ext_transid = ext_transid;
    }

    public String getMobiquity_xml_resp() {
        return mobiquity_xml_resp;
    }

    public void setMobiquity_xml_resp(String mobiquity_xml_resp) {
        this.mobiquity_xml_resp = mobiquity_xml_resp;
    }

    public int getOptionId() {
        return optionId;
    }

    public void setOptionId(int optionId) {
        this.optionId = optionId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getPrice() {
        return price;
    }

    public void setAmProdId(String amProdId) {
        this.amProdId = amProdId;
    }

    public String getAmProdId() {
        return amProdId;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getOcsDesc() {
        return ocsDesc;
    }

    public void setOcsDesc(String ocsDesc) {
        this.ocsDesc = ocsDesc;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    public int getBand_id() {
        return band_id;
    }

    public void setBand_id(int band_id) {
        this.band_id = band_id;
    }

    public String getOcsResp() {
        return ocsResp;
    }

    public void setOcsResp(String ocsResp) {
        this.ocsResp = ocsResp;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getProcessingNode() {
        return processingNode;
    }

    public void setProcessingNode(String processingNode) {
        this.processingNode = processingNode;
    }

    public String getRequestSerial() {
        return requestSerial;
    }

    public void setRequestSerial(String requestSerial) {
        this.requestSerial = requestSerial;
    }

    public String getOcsProdID() {
        return ocsProdID;
    }

    public void setOcsProdID(String ocsProdID) {
        this.ocsProdID = ocsProdID;
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

}//end of class
