package org.airtel.ug.mypk.exceptions;

/**
 *
 * @author Benjamin
 */
public class BundleActivationException extends Exception {

    private int resultCode;
    private String resultDesc;

    public BundleActivationException(int resultCode, String resultDesc) {
        super(resultDesc);
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

}
