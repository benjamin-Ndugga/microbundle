package org.airtel.ug.mypk.exceptions;

import org.airtel.ug.mypk.pojo.OperationResult;

/**
 *
 * @author Benjamin E Ndugga
 */
public class MyPakalastBundleException extends Exception {

    private final int errorCode;
    private final String errMessage;

    public MyPakalastBundleException(String errMessage, int errorCode) {
        super(errMessage);
        this.errMessage = errMessage;
        this.errorCode = errorCode;
    }

    public OperationResult getOperationResult() {
        return new OperationResult(errMessage, errorCode);
    }

}
