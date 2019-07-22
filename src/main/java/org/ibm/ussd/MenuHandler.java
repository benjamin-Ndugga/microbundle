package org.ibm.ussd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 *
 * @author benjamin
 */
public class MenuHandler {

    private static final Logger LOGGER = Logger.getLogger("MICRO_BUNDLE_REQ");

    private static final ArrayList<MicroBundleMenuItem> MENU_LIST = new ArrayList<>();

    static {

        Connection connection = null;
        PreparedStatement statement = null;
        try {

            LOGGER.log(Level.INFO, "CONNECTING TO THE DATABASE");
            LOGGER.log(Level.INFO, "LOADING MENU");

            DataSource dataSource = (DataSource) new InitialContext().lookup("KIKADB");
            connection = dataSource.getConnection();

            statement = connection.prepareStatement("SELECT ID,BAND_ID,OPTION_ID,PRDCOST,PROD_ID,PROD_DT,MENU_ITEM,PROD_ID_AM FROM MICRO_PRODUCTS");

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                MicroBundleMenuItem menuItem = new MicroBundleMenuItem();

                menuItem.setId(resultSet.getInt(1));
                menuItem.setBandId(resultSet.getInt(2));
                menuItem.setOptionId(resultSet.getInt(3));
                menuItem.setPrice(resultSet.getInt(4));
                menuItem.setOcsVoiceSmsProdId(resultSet.getString(5));
                menuItem.setDataProId(resultSet.getString(6));
                menuItem.setMenuItemName(resultSet.getString(7));
                menuItem.setAmVoiceSmsProdId(resultSet.getString(8));

                MENU_LIST.add(menuItem);

                LOGGER.log(Level.INFO, "LOADED-MICRO-MENU {0}", menuItem);
            }

        } catch (NamingException | SQLException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
                }
            }
        }

    }

    /**
     * This returns the Menu For Display for a given Band
     *
     * @param bandId the band for which whose menu will be generated
     *
     * @return the ArrayLis of the Menu to be displayed
     * @throws org.ibm.ussd.MicroBundleException
     */
    public ArrayList<MicroBundleMenuItem> getMenuForDisplay(int bandId) throws MicroBundleException {
        ArrayList<MicroBundleMenuItem> menu_to_display = new ArrayList<MicroBundleMenuItem>();

        for (MicroBundleMenuItem menuItem : MENU_LIST) {

            if (menuItem.getBandId() == bandId) {
                menu_to_display.add(menuItem);
            }
        }

        if (menu_to_display.isEmpty()) {
            throw new MicroBundleException("Failed to process request can not categorise Your Number");
        }

        //sort the menu from small to big according to the option_id
        Collections.sort(menu_to_display);

        return menu_to_display;
    }

    /**
     * Returns the MicroBundleMenuItem from for the band and the option chosen for the band
     *
     * @param bandId
     * @param option_id
     * @return
     * @throws org.ibm.ussd.MicroBundleException
     */
    public MicroBundleMenuItem getMenuItem(int bandId, int option_id) throws MicroBundleException {

        try {
            ArrayList<MicroBundleMenuItem> band_menu = new ArrayList<>();

            for (MicroBundleMenuItem menuItem : MENU_LIST) {
                if (menuItem.getBandId() == bandId) {
                    band_menu.add(menuItem);
                }
            }

            if (band_menu.isEmpty()) {
                throw new MicroBundleException("Failed to process request can not categorise Your Number");
            }

            //sorts from the smalles to the biggest
            Collections.sort(band_menu);

            //based on the positioning of the elements in the arrayList
            //the position is pos -1 to get the menuItem being requested for
            return band_menu.get(option_id - 1);
        } catch (IndexOutOfBoundsException ex) {
            throw new MicroBundleException("Invalid Choice, Please try again!");
        }
    }

}
