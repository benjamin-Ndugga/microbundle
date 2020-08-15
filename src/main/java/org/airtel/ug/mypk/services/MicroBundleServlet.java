package org.airtel.ug.mypk.services;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airtel.ug.mypk.processors.RequestProcessor;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.controllers.MenuController;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.controllers.CacheController;
import org.airtel.ug.mypk.pojo.MicroBundleRequest;
import org.airtel.ug.mypk.util.SMSClient;

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

    @Inject
    private CacheController cacheController;

    @Inject
    private MenuController menuController;

    @Resource(lookup = "resource/am/enabled")
    private int amBillingOption;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String msisdn = request.getParameter("MSISDN");
        String sessionid = request.getParameter("SESSIONID");
        String type = request.getParameter("TYPE");
        String imsi = request.getParameter("IMSI");
        String input = request.getParameter("INPUT");
        PrintWriter out = response.getWriter();
        try {

            LOGGER.log(Level.INFO, "SESSIONID >>> {0} | {1}", new Object[]{sessionid, msisdn});
            LOGGER.log(Level.INFO, "INPUT >>> {0} | {1}", new Object[]{input.replaceAll("\\S", "*"), msisdn});
            LOGGER.log(Level.INFO, "IMSI >>> {0} | {1}", new Object[]{imsi, msisdn});
            LOGGER.log(Level.INFO, "TYPE >>> {0} | {1}", new Object[]{type, msisdn});

            String[] imsi_list = PP_IMSIS.split("\\,");

            List<String> PP_IMSI_PREFIXES = Arrays.asList(imsi_list);

            boolean isPostpaid = PP_IMSI_PREFIXES.contains(imsi.substring(0, 8));

            //send subscription flow message to post-paid subscribers
            if (isPostpaid) {

                LOGGER.log(Level.INFO, "IMSI-IS-A-POST-PAID >>> {0} | {1}", new Object[]{imsi, msisdn});

                out.println("Dear customer, you are not eligible for this service.");

                return;
            }

            //MicroBundleHzClient microBundleHzClient = new CacheController();
            //for first time freeflow control request flash a Menu
            if (type.equals("1")) {

                response.setHeader("Cont", "FC");

                LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", msisdn);

                //get the band for this customer
                int band_id = cacheController.fetchSubscriberBand(msisdn);

                LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, msisdn});

                ArrayList<MenuItem> menu = menuController.getMenuForDisplay(band_id);

                LOGGER.log(Level.INFO, "DISPLAY-MENU | {0}", msisdn);

                out.println("MYPAKALAST");
                out.println("---------------");

                //menu.forEach(out::println);
                int item_no = 1;

                for (MenuItem menuItem : menu) {
                    out.println(item_no + "." + menuItem.getMenuItemName());
                    item_no++;
                }

            } else {

                //get the source of this request
                String src = request.getHeader("X-Real-IP");

                if (src == null) {
                    src = request.getRemoteAddr();
                }

                Integer optionId;

                LOGGER.log(Level.INFO, "IS-AM-OPTION-ENABLED >>> {0} | {1}", new Object[]{amBillingOption, msisdn});

                if (amBillingOption == 0) {

                    optionId = Integer.parseInt(input);

                    response.setHeader("Cont", "FB");
                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    LOGGER.log(Level.INFO, "SUBMIT-TASK-TO-QUEUE | {0}", msisdn);

                    final int optionId_final = (optionId);

                    MicroBundleRequest microBundleRequest = new MicroBundleRequest();
                    microBundleRequest.setMsisdn(msisdn);
                    microBundleRequest.setSourceIp(src);

                    mes.execute(() -> {
                        try {

                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            //fetch the band_id
                            int band_id = cacheController.fetchSubscriberBand(request.getParameter("MSISDN"));

                            LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, request.getParameter("MSISDN")});

                            microBundleRequest.setBandId(band_id);

                            MenuItem menuItem = menuController.getMenuItem(band_id, optionId_final);

                            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM {0} | {1}", new Object[]{menuItem, request.getParameter("MSISDN")});

                            microBundleRequest.setOptionId(optionId_final);
                            microBundleRequest.setImsi(imsi);
                            microBundleRequest.setPin(null);
                            microBundleRequest.setSessionId(sessionid);

                            LOGGER.log(Level.INFO, "EXECUTE-REQUEST: {0}", microBundleRequest);

                            RequestProcessor microBundleRequestProcessor = new RequestProcessor(microBundleRequest, menuItem);
                            microBundleRequestProcessor.run();

                        } catch (Exception ex) {

                            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
                            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());
                        }
                    });

                    return;
                }

                LOGGER.log(Level.INFO, "FETCH-OPTION-ID | {0}", msisdn);

                //check if this is continuing from 1st menu
                optionId = cacheController.getOptionId(sessionid);

                LOGGER.log(Level.INFO, "OPTION-ID: {0} | {1}", new Object[]{optionId, msisdn});

                //if there is no optionId, save the optionId and prompt for the billing option
                if (optionId == null) {

                    int user_input_val = Integer.parseInt(input);

                    validateBundleSelected(user_input_val);

                    cacheController.saveOptionIdAsync(sessionid, user_input_val);

                    LOGGER.log(Level.INFO, "PROMPT-BILLING-OPTION | {0}", msisdn);

                    response.setHeader("Cont", "FC");

                    out.println("Please select billing option:");
                    out.println("1.Airtel Money.");
                    out.println("2.Airtime.");
                    out.println("#.to quit");

                    return;
                }

                //get the billing option selected
                Integer billingOption = cacheController.getBillingOption(sessionid);

                if (billingOption == null) {

                    //if the billingOption/INPUT is 1 then save and prompt for the PIN 
                    if (input.equals("1")) {

                        cacheController.saveBillingOptionAsync(sessionid, Integer.parseInt(input));

                        LOGGER.log(Level.INFO, "PROMPT-PIN | {0}", msisdn);

                        response.setHeader("Cont", "FC");
                        out.println("Please Enter PIN:");

                        return;
                    } else {
                        LOGGER.log(Level.INFO, "SETTING-DEFAULT-TO-AT-BILLING-OPTION | {0}", msisdn);

                        //set the billingoption to deafault 2
                        billingOption = 2;

                        LOGGER.log(Level.INFO, "BILLING-OPTION: {0} | {1}", new Object[]{billingOption, msisdn});
                    }
                }

                if (billingOption == 2) {

                    //proceed to process Airtime Request
                    response.setHeader("Cont", "FB");
                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    MicroBundleRequest microBundleRequest = new MicroBundleRequest();
                    microBundleRequest.setMsisdn(msisdn);
                    microBundleRequest.setImsi(imsi);
                    microBundleRequest.setPin(null);
                    microBundleRequest.setSessionId(sessionid);
                    microBundleRequest.setOptionId(optionId);
                    microBundleRequest.setSourceIp(src);

                    LOGGER.log(Level.INFO, "SUBMIT-REQUEST-TO-QUEUE | {0}", microBundleRequest);

                    mes.execute(() -> {
                        try {

                            LOGGER.log(Level.INFO, "BUILDING-REQUEST | {0}", microBundleRequest);

                            //fetch the subscriber's service class
                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            //fetch the subscriber's band
                            Integer bandId = cacheController.fetchSubscriberBand(msisdn);

                            microBundleRequest.setBandId(bandId);

                            MenuItem menuItem = menuController.getMenuItem(bandId, optionId);

                            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM: {0} | {1}", new Object[]{menuItem, msisdn});

                            LOGGER.log(Level.INFO, "RUNNING-REQUEST: {0}", microBundleRequest);

                            RequestProcessor microBundleRequestProcessor = new RequestProcessor(microBundleRequest, menuItem);

                            LOGGER.log(Level.INFO, "PROCESSING >>> {0}", microBundleRequestProcessor);

                            microBundleRequestProcessor.run();

                        } catch (Exception ex) {
                            ex.printStackTrace(System.err);
                        }

                    });

                } else {
                    //proceed to process Airtel Money Request
                    LOGGER.log(Level.INFO, "AIRTEL-MONEY-REQUEST | {0}", msisdn);
                    
                    response.setHeader("Cont", "FB");
                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    LOGGER.log(Level.INFO, "SUBMIT-TASK-TO-QUEUE | {0}", msisdn);

                    MicroBundleRequest microBundleRequest = new MicroBundleRequest();
                    microBundleRequest.setMsisdn(msisdn);
                    microBundleRequest.setImsi(imsi);
                    microBundleRequest.setPin(input);
                    microBundleRequest.setSessionId(sessionid);
                    microBundleRequest.setOptionId(optionId);
                    microBundleRequest.setSourceIp(src);

                    mes.execute(() -> {
                        try {

                            LOGGER.log(Level.INFO, "BUILDING-REQUEST | {0}", microBundleRequest);

                            //fetch the subscriber's service class
                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            //fetch the subscriber's band
                            Integer bandId = cacheController.fetchSubscriberBand(msisdn);

                            microBundleRequest.setBandId(bandId);

                            MenuItem menuItem = menuController.getMenuItem(bandId, optionId);

                            LOGGER.log(Level.INFO, "SELECTED-MENU-ITEM: {0} | {1}", new Object[]{menuItem, msisdn});

                            LOGGER.log(Level.INFO, "RUNNING-REQUEST: {0}", microBundleRequest);

                            RequestProcessor microBundleRequestProcessor = new RequestProcessor(microBundleRequest, menuItem);

                            LOGGER.log(Level.INFO, "PROCESSING >>> {0}", microBundleRequestProcessor);

                            microBundleRequestProcessor.run();

                        } catch (Exception ex) {
                            ex.printStackTrace(System.err);
                        }
                    });
                }
            }

        } catch (MyPakalastBundleException | NumberFormatException ex) {

            LOGGER.log(Level.INFO, "{0} | {1}", new Object[]{ex.getLocalizedMessage(), msisdn});

            response.setHeader("Cont", "FB");
            out.println(ex.getLocalizedMessage());

        } catch (RejectedExecutionException ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            response.setHeader("Cont", "FB");
            out.println("Your request failed to be processed, please try again later.");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            response.setHeader("Cont", "FB");
            out.println("Your request failed to be processed, please try again later.");

        } finally {
            out.close();
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
            throw new MyPakalastBundleException("Invalid choice please choose between options 1-3.", 126);
        }
    }

}
