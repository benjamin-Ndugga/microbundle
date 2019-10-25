package org.airtel.ug.mypk.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.util.HzClient;
import org.airtel.ug.mypk.util.MyPakalastBundleException;

/**
 * 10-Dec-2018 written in support based on the ARPU for voice subscribers
 *
 * @author benjamin
 */
@WebServlet(urlPatterns = "/Client")
public class Client extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger("MYPAKALAST");

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        InitialContext ic = null;

        String MSISDN = request.getParameter("MSISDN");
        String SESSIONID = request.getParameter("SESSIONID");
        String TYPE = request.getParameter("TYPE");
        String IMSI = request.getParameter("IMSI");
        String INPUT = request.getParameter("INPUT");

        HzClient hzClient = new HzClient();

        try {

            //************************************************************
            //retrive post-paid imsis
            ic = new InitialContext();
            String ppImsis = (String) ic.lookup("resource/ppimsis");

            String[] imsi_list = ppImsis.split("\\,");

            List<String> PP_IMSI_PREFIXES = Arrays.asList(imsi_list);

            boolean isPostpaid = PP_IMSI_PREFIXES.contains(IMSI.substring(0, 8));

            // THIS SECTION ELIMINATES ALL POSTPAID NUMBERS
            if (isPostpaid) {

                LOGGER.log(Level.INFO, "IMSI IS A PREPAID | {0} ", new Object[]{IMSI});

                out.println("Dear Customer, you are not eligible for this service.");

                return;
            }

            LOGGER.log(Level.INFO, "SESSION_ID {0} | {1}", new Object[]{SESSIONID, MSISDN});
            LOGGER.log(Level.INFO, "INPUT {0} | {1}", new Object[]{INPUT, MSISDN});
            LOGGER.log(Level.INFO, "IMSI {0} | {1}", new Object[]{IMSI, MSISDN});

            ArrayList<MenuItem> menu;

            //for first time freeflow control request flash a Menu
            if (TYPE.equals("1")) {

                response.setHeader("Cont", "FC");

                LOGGER.log(Level.INFO, "LOOKUP_CUSTOMER BAND | {0}", MSISDN);

                //get the band for this customer
                int band_id = hzClient.getBand(MSISDN);

                LOGGER.log(Level.INFO, "BAND_ID FOUND {0} | {1}", new Object[]{band_id, MSISDN});

                menu = new MenuHandler().getMenuForDisplay(band_id);

                LOGGER.log(Level.INFO, "BUILDING_MENU | {0}", MSISDN);

                out.println("MYPAKALAST");
                out.println("---------------");

                //menu.forEach(out::println);
                int item_no = 1;

                for (MenuItem menuItem : menu) {
                    out.println(item_no + "." + menuItem.getMenuItemName());
                    item_no ++;
                }

                LOGGER.log(Level.INFO, "DISPLAY_MENU | {0}", MSISDN);

            } else {
                Integer billingOption = 2;
                Integer optionId = Integer.parseInt(INPUT);

//                LOGGER.log(Level.INFO, "CHECK_OPTION_ID | {0}", MSISDN);
//
//                //check if this is continuing from 1st menu
//                Integer optionId = hzClient.getOptionId(MSISDN);
//
//                LOGGER.log(Level.INFO, "OPTION_ID_VALUE {0} | {1}", new Object[]{optionId, MSISDN});
//
//                //if there is no optionId, save the optionId and prompt for the billing option
//                if (optionId == null) {
//
//                    LOGGER.log(Level.INFO, "PROMPT_BILLING_OPTION | {0}", MSISDN);
//
//                    hzClient.saveOptionId(MSISDN, Integer.parseInt(INPUT));
//
//                    response.setHeader("Cont", "FC");
//
//                    out.println("Please select billing option:");
//                    out.println("1.Airtel Money.");
//                    out.println("2.Airtime.");
//
//                    return;
//                }
//
//                LOGGER.log(Level.INFO, "CHECK_BILLING_OPTION | {0}", MSISDN);
//
//                //get the billing option selected
//                Integer billingOption = hzClient.getBillingOption(MSISDN);
//
//                LOGGER.log(Level.INFO, "BILLING_OPTION_FOUND {0} | {1}", new Object[]{billingOption, MSISDN});
//
//                if (billingOption == null) {
//
//                    LOGGER.log(Level.INFO, "SAVE BILLING OPTION {0} | {1}", new Object[]{INPUT, MSISDN});
//
//                    hzClient.saveBillingOption(MSISDN, INPUT);
//
//                    LOGGER.log(Level.INFO, "PROMPT_PIN | {0}", MSISDN);
//
//                    //if the billingOption sent throught the INPUT is 1 then prompt for the PIN 
//                    if (INPUT.equals("1")) {
//
//                        response.setHeader("Cont", "FC");
//
//                        out.println("Please Enter PIN:");
//
//                        return;
//                    } else {
//
//                        //set the billingoption to 2
//                        billingOption = 2;
//
//                    }
//                }
                //get the source of this request
                String src = request.getHeader("X-Real-IP");

                if (src == null) {
                    src = request.getRemoteAddr();
                }

                LOGGER.log(Level.INFO, "REQUEST_SENT_FROM {0} | {1}", new Object[]{src, MSISDN});

                if (billingOption == 2) {
                    //proceed to process Airtime Request

                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-Airtime | {0}", MSISDN);

                    out.println("Your request is being processed. Please wait for confirmation SMS.");

                    mes.submit(new RequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI));

                } else {
                    //proceed to process Airtel Money Request
//                    response.setHeader("Cont", "FB");
//
//                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", MSISDN);
//
//                    out.println("Your request is being processed. Please wait for confirmation SMS.");
//
//                    mes.execute(new RequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, INPUT));
                }
            }
        } catch (NumberFormatException ex) {

            LOGGER.log(Level.INFO, "INVALID-INPUT | {0}", MSISDN);

            response.setHeader("Cont", "FB");

            out.println("Invalid input, Please try again.");

            hzClient.clearSessionData(MSISDN);

        } catch (MyPakalastBundleException ex) {

            response.setHeader("Cont", "FB");

            out.println(ex.getLocalizedMessage());

            hzClient.clearSessionData(MSISDN);

        } catch (IllegalStateException | IndexOutOfBoundsException | NullPointerException | NamingException | RejectedExecutionException ex) {

            response.setHeader("Cont", "FB");

            out.println("Your request can not be processed at the moment!,Please try again later");

            hzClient.clearSessionData(MSISDN);

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        } finally {
            out.close();

            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }//end of process request

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
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
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
}
