package org.airtel.ug.mypk.util;

/**
 *
 * @author benjamin
 */
public class MicroBundleMenuItem implements Comparable<MicroBundleMenuItem> {

    private int id;
    private int bandId;
    private int optionId;
    private int price;
    private String ocsVoiceSmsProdId;
    private String dataProId;
    private String amVoiceSmsProdId;
    private String menuItemName;

    public String getOcsVoiceSmsProdId() {
        return ocsVoiceSmsProdId;
    }

    public void setOcsVoiceSmsProdId(String ocsVoiceSmsProdId) {
        this.ocsVoiceSmsProdId = ocsVoiceSmsProdId;
    }

    public String getDataProId() {
        return dataProId;
    }

    public void setDataProId(String dataProId) {
        this.dataProId = dataProId;
    }

    public String getAmVoiceSmsProdId() {
        return amVoiceSmsProdId;
    }

    public void setAmVoiceSmsProdId(String amVoiceSmsProdId) {
        this.amVoiceSmsProdId = amVoiceSmsProdId;
    }

    public MicroBundleMenuItem() {
    }

    public String getMenuItemName() {
        return menuItemName;
    }

    public void setMenuItemName(String menuItemName) {
        this.menuItemName = menuItemName;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
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

    public int getId() {
        return id;
    }

    public void setOptionId(int optionId) {
        this.optionId = optionId;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return this.optionId + "." + this.menuItemName;
    }

    @Override
    public int compareTo(MicroBundleMenuItem m) {
        //-1 when is less
        //0 when equal to
        //+1 greater than
        if (m.getOptionId() < this.getOptionId()) {
            return 1;
        } else if (m.getOptionId() == this.getOptionId()) {
            return 0;
        } else {
            return -1;
        }
    }

}
