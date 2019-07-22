package org.airtel.ug.util;

/**
 *
 * @author benjamin
 */
public class MenuItem implements Comparable<MenuItem> {

    private int id;
    private int bandId;
    private int optionId;
    private int price;
    private String ocsProdId;
    private String amProdId;
    private String menuItemName;

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

    public MenuItem() {
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
    public int compareTo(MenuItem m) {
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
