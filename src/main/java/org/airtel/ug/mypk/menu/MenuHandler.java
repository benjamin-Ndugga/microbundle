package org.airtel.ug.mypk.menu;

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
import org.airtel.ug.mypk.util.MyPakalastBundleException;

/**
 *
 * @author benjamin
 */
public class MenuHandler {

    private static final Logger LOGGER = Logger.getLogger("MYPK");

    private static final ArrayList<MenuItem> MENU_LIST = new ArrayList<>();

    static {

        Connection connection = null;
        PreparedStatement statement;
        try {

            LOGGER.log(Level.INFO, "LOADING_MENU");

            DataSource dataSource = (DataSource) new InitialContext().lookup("KIKADB");
            connection = dataSource.getConnection();

            statement = connection.prepareStatement("SELECT "
                    + "ID,"
                    + "BAND_ID,"
                    + "PROD_ID,"
                    + "MENU_ITEM,"
                    + "OPTION_ID,"
                    + "PRDCOST,"
                    + "PROD_ID_AM "
                    + "FROM "
                    + "MICRO_PRODUCTS");

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                MenuItem menuItem = new MenuItem();

                menuItem.setId(resultSet.getInt(1));
                menuItem.setBandId(resultSet.getInt(2));
                menuItem.setOcsProdId(resultSet.getString(3));
                menuItem.setMenuItemName(resultSet.getString(4));
                menuItem.setOptionId(resultSet.getInt(5));
                menuItem.setPrice(resultSet.getInt(6));
                //menuItem.setDataProdId(resultSet.getString(7));
                menuItem.setAmProdId(resultSet.getString(7));

                MENU_LIST.add(menuItem);

                LOGGER.log(Level.INFO, "LOADED-MENU {0}", menuItem.printLogFormat());
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
     * @throws org.airtel.ug.mypk.util.MyPakalastBundleException
     */
    public ArrayList<MenuItem> getMenuForDisplay(int bandId) throws MyPakalastBundleException {
        ArrayList<MenuItem> menu_to_display = new ArrayList<>();

        MENU_LIST.stream()
                .filter((menuItem) -> (menuItem.getBandId() == bandId))
                .forEachOrdered((menuItem) -> {
                    menu_to_display.add(menuItem);
                });
        
       
        if (menu_to_display.isEmpty()) {
            //throw new MyPakalastBundleException("Failed to process request can not categorise Your Number");
            throw new MyPakalastBundleException("You are ineligible for this service.Dial *149# to select another bundle of your choice.");
        }

        //sort the menu from small to big according to the option_id
        Collections.sort(menu_to_display);

        return menu_to_display;
    }

    /**
     * Returns the MenuItem from for the band and the option chosen for the band
     *
     * @param bandId
     * @param option_id
     * @return
     * @throws org.airtel.ug.mypk.util.MyPakalastBundleException
     */
    public MenuItem getMenuItem(int bandId, int option_id) throws MyPakalastBundleException {

        try {
            ArrayList<MenuItem> band_menu = new ArrayList<>();

            MENU_LIST.stream()
                    .filter((menuItem) -> (menuItem.getBandId() == bandId))
                    .forEachOrdered((menuItem) -> {
                        band_menu.add(menuItem);
                    });

            if (band_menu.isEmpty()) {
                throw new MyPakalastBundleException("Failed to process request can not categorise Your Number");
            }

            //sorts based on the comparable implementation
            Collections.sort(band_menu);

            //based on the positioning of the elements in the arrayList
            //the position is pos -1 to get the menuItem being requested for
            return band_menu.get(option_id - 1);
        } catch (IndexOutOfBoundsException ex) {
            throw new MyPakalastBundleException("Invalid Choice, Please try again!");
        }
    }

}
