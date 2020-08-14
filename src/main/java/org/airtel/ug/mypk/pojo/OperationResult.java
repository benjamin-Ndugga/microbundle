package org.airtel.ug.mypk.pojo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * <p>
 * This class is annotated with the JXAB 2.0 to allow for easy JSON Parsing for
 * RESTFul web-service calls
 * </p>
 * <em><b>Note:</b> Changes to this class definitions greatly affect the way
 * Clients parse the responses for the requests</em>
 *
 * @version 2.2
 * @author Benjamin E Ndugga
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class OperationResult {

    private String resultDesc;
    private int resultCode;

    public OperationResult() {
    }

    public OperationResult(String resultDesc, int resultCode) {
        this.resultCode = resultCode;
        this.resultDesc = resultDesc;
    }

    public int getResultCode() {
        return resultCode;
    }

    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultDesc() {
        return resultDesc;
    }

    public void setResultDesc(String resultDesc) {
        this.resultDesc = resultDesc;
    }
}//end of class
