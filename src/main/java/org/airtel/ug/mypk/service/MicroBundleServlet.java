package org.airtel.ug.mypk.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airtel.ug.mypk.controllers.MicroBundleRequestProcessor;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.util.MicroBundleHzClient;

/**
 *
 * @author Benjamin E Ndugga
 */
@WebServlet(name = "MicroBundleServlet", urlPatterns = {"/Client"})
public class MicroBundleServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleServlet.class.getName());


    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Resource(lookup = "resource/ppimsis")
    private String PP_IMSIS;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String MSISDN = request.getParameter("MSISDN");
        String SESSIONID = request.getParameter("SESSIONID");
        String TYPE = request.getParameter("TYPE");
        String IMSI = request.getParameter("IMSI");
        String INPUT = request.getParameter("INPUT");

        try (PrintWriter out = response.getWriter()) {

            LOGGER.log(Level.INFO, "SESSIONID >>> {0} | {1}", new Object[]{SESSIONID, MSISDN});
            LOGGER.log(Level.INFO, "INPUT >>> {0} | {1}", new Object[]{INPUT.replaceAll("\\S", "*"), MSISDN});
            LOGGER.log(Level.INFO, "IMSI >>> {0} | {1}", new Object[]{IMSI, MSISDN});
            LOGGER.log(Level.INFO, "TYPE >>> {0} | {1}", new Object[]{TYPE, MSISDN});

            String[] imsi_list = PP_IMSIS.split("\\,");

            List<String> PP_IMSI_PREFIXES = Arrays.asList(imsi_list);

            boolean isPostpaid = PP_IMSI_PREFIXES.contains(IMSI.substring(0, 8));

            //send subscription flow message to post-paid subscribers
            if (isPostpaid) {

                LOGGER.log(Level.INFO, "IMSI-IS-A-POST-PAID >>> {0} | {1}", new Object[]{IMSI, MSISDN});

                out.println("Dear customer, you are not eligible for this service.");

                return;
            }

            MicroBundleHzClient microBundleHzClient = new MicroBundleHzClient();

            //for first time freeflow control request flash a Menu
            if (TYPE.equals("1")) {

                response.setHeader("Cont", "FC");

                LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", MSISDN);

                //get the band for this customer
                int band_id = microBundleHzClient.getBand(MSISDN);

                LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, MSISDN});

                ArrayList<MenuItem> menu = new MenuHandler().getMenuForDisplay(band_id);

                LOGGER.log(Level.INFO, "DISPLAY-MENU | {0}", MSISDN);

                out.println("MYPAKALAST");
                out.println("---------------");

                //menu.forEach(out::println);
                int item_no = 1;

                for (MenuItem menuItem : menu) {
                    out.println(item_no + "." + menuItem.getMenuItemName());
                    item_no++;
                }

            } else {

                LOGGER.log(Level.INFO, "FETCH-OPTION-ID | {0}", MSISDN);

                //check if this is continuing from 1st menu
                Integer optionId = microBundleHzClient.getOptionId(SESSIONID);

                LOGGER.log(Level.INFO, "OPTION-ID: {0} | {1}", new Object[]{optionId, MSISDN});

                //if there is no optionId, save the optionId and prompt for the billing option
                if (optionId == null) {

                    validateBundleSelected(Integer.parseInt(INPUT));

                    microBundleHzClient.saveOptionIdAsync(SESSIONID, Integer.parseInt(INPUT));

                    LOGGER.log(Level.INFO, "PROMPT-BILLING-OPTION | {0}", MSISDN);

                    response.setHeader("Cont", "FC");

                    out.println("Please select billing option:");
                    out.println("1.Airtel Money.");
                    out.println("2.Airtime.");
                    out.println("#.to quit");

                    return;
                }

                //get the billing option selected
                Integer billingOption = microBundleHzClient.getBillingOption(SESSIONID);

                if (billingOption == null) {

                    //if the billingOption/INPUT is 1 then save and prompt for the PIN 
                    if (INPUT.equals("1")) {

                        microBundleHzClient.saveBillingOptionAsync(SESSIONID, Integer.parseInt(INPUT));

                        LOGGER.log(Level.INFO, "PROMPT-PIN | {0}", MSISDN);

                        response.setHeader("Cont", "FC");
                        out.println("Please Enter PIN:");

                        return;
                    } else {
                        LOGGER.log(Level.INFO, "SETTING-DEFAULT-TO-AT-BILLING-OPTION | {0}", MSISDN);
                        LOGGER.log(Level.INFO, "BILLING-OPTION: {0} | {1}", new Object[]{billingOption, MSISDN});
                        //set the billingoption to deafault 2
                        billingOption = 2;
                    }
                }
                //get the source of this request
                String src = request.getHeader("X-Real-IP");

                if (src == null) {
                    src = request.getRemoteAddr();
                }

                if (billingOption == 2) {
                    //proceed to process Airtime Request

                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-Airtime | {0}", MSISDN);

                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    mes.submit(new MicroBundleRequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, null));

                } else {
                    //proceed to process Airtel Money Request
                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", MSISDN);

                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    mes.execute(new MicroBundleRequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, INPUT));
                }
            }

        } catch (MyPakalastBundleException ex) {
            LOGGER.log(Level.INFO, "{0} | {1}", new Object[]{ex.getLocalizedMessage(), MSISDN});
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    public void validateBundleSelected(int input) throws MyPakalastBundleException {

        LOGGER.log(Level.INFO, "VALIDATE-BUNDLE-SELECTED");

        if (input < 1 || input > 3) {
            LOGGER.log(Level.INFO, "INVALID-CHOICE-SELECTED >> {0}", input);
            throw new MyPakalastBundleException("Invalid choice please choose between options 1-3.");
        }
    }

}
