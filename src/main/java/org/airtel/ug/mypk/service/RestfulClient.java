package org.airtel.ug.mypk.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.airtel.ug.mypk.menu.MenuHandler;
import org.airtel.ug.mypk.menu.MenuItem;
import org.airtel.ug.mypk.util.HzClient;
import org.airtel.ug.mypk.util.MyPakalastBundleException;


/**
 *
 * @author Benjamin
 */
@Path("/")
public class RestfulClient {

    private static final Logger LOGGER = Logger.getLogger("MYPAKALAST");

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getWelcomeMsg(@Context HttpHeaders headers) {

        LOGGER.log(Level.INFO, "APPLICATION-LOGS!");

        return "<p>Restful MYPAKLAST Application</p>";
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/am")
    public String processAMRequestPost(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            @QueryParam("MSISDN") String MSISDN,
            @QueryParam("SESSIONID") String SESSIONID,
            @QueryParam("INPUT") String INPUT,
            @QueryParam("IMSI") String IMSI,
            @QueryParam("TYPE") String TYPE) {

        return processAirtelMoneyRequest(request, response, MSISDN, SESSIONID, TYPE, IMSI, INPUT);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/am")
    public String processAMRequestGet(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            @QueryParam("MSISDN") String MSISDN,
            @QueryParam("SESSIONID") String SESSIONID,
            @QueryParam("INPUT") String INPUT,
            @QueryParam("IMSI") String IMSI,
            @QueryParam("TYPE") String TYPE) {

        return processAirtelMoneyRequest(request, response, MSISDN, SESSIONID, TYPE, IMSI, INPUT);
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/ussd")
    public String processUSSDRequestPost(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            @QueryParam("MSISDN") String MSISDN,
            @QueryParam("SESSIONID") String SESSIONID,
            @QueryParam("INPUT") String INPUT,
            @QueryParam("IMSI") String IMSI,
            @QueryParam("TYPE") String TYPE) {

        return processUssdRequest(request, response, MSISDN, SESSIONID, TYPE, IMSI, INPUT);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/ussd")
    public String processUSSDRequestGet(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            @QueryParam("MSISDN") String MSISDN,
            @QueryParam("SESSIONID") String SESSIONID,
            @QueryParam("INPUT") String INPUT,
            @QueryParam("IMSI") String IMSI,
            @QueryParam("TYPE") String TYPE) {

        return processUssdRequest(request, response, MSISDN, SESSIONID, TYPE, IMSI, INPUT);
    }

    private String processAirtelMoneyRequest(HttpServletRequest request, HttpServletResponse response, String MSISDN, String SESSIONID, String TYPE, String IMSI, String INPUT) {

        InitialContext ic = null;

        HzClient hzClient = new HzClient();

        String responseTxt = "";

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

                return "Dear Customer, you are not eligible for this service.";

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

                responseTxt += "MYPAKALAST\n";
                responseTxt += "---------------\n";

                for (MenuItem menuItem : menu) {
                    responseTxt += menuItem + "\n";
                }

                LOGGER.log(Level.INFO, "DISPLAY_MENU | {0}", MSISDN);

                return responseTxt;
            } else {

                Integer optionId = hzClient.getOptionId(MSISDN);

                if (optionId == null) {

                    LOGGER.log(Level.INFO, "SAVE_OPTION_ID | {0}", MSISDN);

                    hzClient.saveOptionId(MSISDN, Integer.parseInt(INPUT));

                    response.setHeader("Cont", "FC");

                    responseTxt += "Please Enter PIN:\n";

                    return responseTxt;
                }

                //get the source of this request
                String src = request.getHeader("X-Real-IP");

                if (src == null) {
                    src = request.getRemoteAddr();
                }

                LOGGER.log(Level.INFO, "REQUEST_SENT_FROM {0} | {1}", new Object[]{src, MSISDN});

                //proceed to process Airtel Money Request
                response.setHeader("Cont", "FB");

                LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", MSISDN);

                responseTxt += "Your request is being processed. Please wait for confirmation SMS.";

                new Thread(new RequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, INPUT)).start();

                return responseTxt;

            }
        } catch (MyPakalastBundleException ex) {

            response.setHeader("Cont", "FB");

            responseTxt += ex.getLocalizedMessage();

            hzClient.clearSessionData(MSISDN);

            return responseTxt;

        } catch (IllegalStateException | IndexOutOfBoundsException | NullPointerException | NamingException ex) {

            response.setHeader("Cont", "FB");

            responseTxt += "Your request can not be processed at the moment!,Please try again later";

            hzClient.clearSessionData(MSISDN);

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            return responseTxt;

        } catch (NumberFormatException ex) {

            response.setHeader("Cont", "FB");

            responseTxt += "Invalid option selected, Please try again!";

            hzClient.clearSessionData(MSISDN);

            LOGGER.log(Level.INFO, ex.getLocalizedMessage(), ex);

            return responseTxt;

        } finally {

            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }//end of process request

    private String processUssdRequest(HttpServletRequest request, HttpServletResponse response, String MSISDN, String SESSIONID, String TYPE, String IMSI, String INPUT) {

        InitialContext ic = null;

        HzClient hzClient = new HzClient();

        String responseTxt = "";

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

                return "Dear Customer, you are not eligible for this service.";

            }

            LOGGER.log(Level.INFO, "SESSION_ID {0} | {1}", new Object[]{SESSIONID, MSISDN});
            LOGGER.log(Level.INFO, "INPUT {0} | {1}", new Object[]{INPUT, MSISDN});
            LOGGER.log(Level.INFO, "IMSI {0} | {1}", new Object[]{IMSI, MSISDN});

            ArrayList<MenuItem> menu = null;

            //for first time freeflow control request flash a Menu
            if (TYPE.equals("1")) {

                response.setHeader("Cont", "FC");

                LOGGER.log(Level.INFO, "LOOKUP_CUSTOMER BAND | {0}", MSISDN);

                //get the band for this customer
                int band_id = hzClient.getBand(MSISDN);

                LOGGER.log(Level.INFO, "BAND_ID FOUND {0} | {1}", new Object[]{band_id, MSISDN});

                menu = new MenuHandler().getMenuForDisplay(band_id);

                LOGGER.log(Level.INFO, "BUILDING_MENU | {0}", MSISDN);

                responseTxt += "MYPAKALAST\n";
                responseTxt += "---------------\n";

                for (MenuItem menuItem : menu) {
                    responseTxt += menuItem + "\n";
                }

                LOGGER.log(Level.INFO, "DISPLAY_MENU | {0}", MSISDN);

                return responseTxt;

            } else {

                LOGGER.log(Level.INFO, "CHECK_OPTION_ID | {0}", MSISDN);

                //check if this is continuing from 1st menu
                Integer optionId = hzClient.getOptionId(MSISDN);

                LOGGER.log(Level.INFO, "OPTION_ID_VALUE {0} | {1}", new Object[]{optionId, MSISDN});

                //if there is no optionId, save the optionId and prompt for the billing option
                if (optionId == null) {

                    LOGGER.log(Level.INFO, "PROMPT_BILLING_OPTION | {0}", MSISDN);

                    hzClient.saveOptionId(MSISDN, Integer.parseInt(INPUT));

                    response.setHeader("Cont", "FC");

                    responseTxt += "Please select billing option:\n";
                    responseTxt += "1.Airtel Money.\n";
                    responseTxt += "2.Airtime.\n";

                    return responseTxt;
                }

                LOGGER.log(Level.INFO, "CHECK_BILLING_OPTION | {0}", MSISDN);

                //get the billing option selected
                Integer billingOption = hzClient.getBillingOption(MSISDN);

                LOGGER.log(Level.INFO, "BILLING_OPTION_FOUND {0} | {1}", new Object[]{billingOption, MSISDN});

                if (billingOption == null) {

                    LOGGER.log(Level.INFO, "SAVE BILLING OPTION {0} | {1}", new Object[]{INPUT, MSISDN});

                    hzClient.saveBillingOption(MSISDN, INPUT);

                    LOGGER.log(Level.INFO, "PROMPT_PIN | {0}", MSISDN);

                    //if the billingOption sent throught the INPUT is 1 then prompt for the PIN 
                    if (INPUT.equals("1")) {

                        response.setHeader("Cont", "FC");

                        responseTxt += "Please Enter PIN:\n";

                        return responseTxt;
                    } else {

                        //set the billingoption to 2 for Airtime
                        billingOption = 2;

                    }
                }

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

                    responseTxt += "Your request is being processed. Please wait for confirmation SMS.";

                    new Thread(new RequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI)).start();

                    return responseTxt;

                } else {
                    //proceed to process Airtel Money Request
                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", MSISDN);

                    responseTxt += "Your request is being processed. Please wait for confirmation SMS.";

                    new Thread(new RequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, INPUT)).start();

                    return responseTxt;
                }

            }
        } catch (MyPakalastBundleException ex) {

            response.setHeader("Cont", "FB");

            responseTxt += ex.getLocalizedMessage();

            hzClient.clearSessionData(MSISDN);

            return responseTxt;

        } catch (IllegalStateException | IndexOutOfBoundsException | NullPointerException | NumberFormatException | NamingException ex) {

            response.setHeader("Cont", "FB");

            responseTxt += "Your request can not be processed at the moment!,Please try again later";

            hzClient.clearSessionData(MSISDN);

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

            return responseTxt;
        } finally {

            if (ic != null) {
                try {
                    ic.close();
                } catch (NamingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }//end of process request

}
