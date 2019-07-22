package org.airtel.ug.am;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author Benjamin
 */
public class MobiquityReponseHandler extends DefaultHandler {

    private boolean isTxnstatus = false;
    private boolean isCommand = false;
    private boolean isMessage = false;
    private boolean isExtra = false;
    private boolean isTxnid = false;

    private String txnstatus = "-1";
    private String command;
    private String message;
    private String extra;
    private String txnid;

    public String getTxnstatus() {
        return txnstatus;
    }

    public String getCommand() {
        return command;
    }

    public String getMessage() {
        return message;
    }

    public String getExtra() {
        return extra;
    }

    public String getTxnid() {
        return txnid;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        if (qName.equalsIgnoreCase("TXNSTATUS")) {
            isTxnstatus = true;
        }

        if (qName.equalsIgnoreCase("COMMAND")) {
            isCommand = true;
        }

        if (qName.equalsIgnoreCase("MESSAGE")) {
            isMessage = true;
        }

        if (qName.equalsIgnoreCase("EXTRA")) {
            isExtra = true;
        }

        if (qName.equalsIgnoreCase("TXNID")) {
            isTxnid = true;
        }

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        if (isTxnstatus) {
            txnstatus = new String(ch, start, length);
            isTxnstatus = false;
        }

        if (isCommand) {
            command = new String(ch, start, length);
            isCommand = false;
        }

        if (isMessage) {
            message = new String(ch, start, length);
            isMessage = false;
        }

        if (isTxnid) {
            txnid = new String(ch, start, length);
            isTxnid = false;
        }

        if (isExtra) {
            extra = new String(ch, start, length);
            isExtra = false;
        }

    }

}
