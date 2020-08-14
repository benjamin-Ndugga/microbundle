
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
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airtel.ug.mypk.controllers.CacheController;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.controllers.MenuController;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.pojo.MicroBundleRequest;
import org.airtel.ug.mypk.processors.MicroBundleRequestProcessor;
import org.airtel.ug.mypk.util.SMSClient;

/**
 *
 * @author Benjamin E Ndugga
 */
@ApplicationScoped
public class RequestController {

    private static final Logger LOGGER = Logger.getLogger(RequestController.class.getName());

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Resource(lookup = "resource/ppimsis")
    private String PP_IMSIS;

    @Inject
    private CacheController cacheController;

    @Resource(lookup = "resource/am/enabled")
    private int amBillingOption;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response, String msisdn, String sessionid, String imsi, String input, String type) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {

            out = response.getWriter();

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

                ArrayList<MenuItem> menu = new MenuController().getMenuForDisplay(band_id);

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
                Integer optionId;

                LOGGER.log(Level.INFO, "IS-AM-OPTION-ENABLED >>> {0} | {1}", new Object[]{amBillingOption, msisdn});

                if (amBillingOption == 0) {

                    optionId = Integer.parseInt(input);

                    response.setHeader("Cont", "FB");
                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    LOGGER.log(Level.INFO, "SUBMIT-TASK-TO-QUEUE | {0}", msisdn);

                    final int optionId_final = (optionId);

                    mes.execute(() -> {
                        try {
                            MicroBundleRequest microBundleRequest = new MicroBundleRequest();

                            //fetch the band_id
                            int band_id = cacheController.fetchSubscriberBand(msisdn);

                            LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, msisdn});

                            microBundleRequest.setBandId(band_id);

                            MenuItem menuItem = new MenuController().getMenuItem(band_id, optionId_final);

                            microBundleRequest.setOptionId(optionId_final);
                            microBundleRequest.setImsi(imsi);
                            microBundleRequest.setPin(null);
                            microBundleRequest.setSessionId(sessionid);

                            //get the source of this request
                            String src = request.getHeader("X-Real-IP");

                            if (src == null) {
                                src = request.getRemoteAddr();
                            }

                            microBundleRequest.setSourceIp(src);

                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            LOGGER.log(Level.INFO, "excecute: {0}", microBundleRequest);

                            MicroBundleRequestProcessor microBundleRequestProcessor = new MicroBundleRequestProcessor(microBundleRequest, menuItem);
                            microBundleRequestProcessor.run();

                        } catch (MyPakalastBundleException ex) {
                            LOGGER.log(Level.INFO, ex.getLocalizedMessage());
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
                        LOGGER.log(Level.INFO, "BILLING-OPTION: {0} | {1}", new Object[]{billingOption, msisdn});
                        //set the billingoption to deafault 2
                        billingOption = 2;
                    }
                }

                if (billingOption == 2) {
                    //proceed to process Airtime Request

                    response.setHeader("Cont", "FB");

                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    LOGGER.log(Level.INFO, "SUBMIT-TASK-TO-QUEUE | {0}", msisdn);

                    final int optionId_final = (optionId);

                    mes.execute(() -> {
                        try {
                            MicroBundleRequest microBundleRequest = new MicroBundleRequest();

                            //fetch the band_id
                            int band_id = cacheController.fetchSubscriberBand(msisdn);

                            LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, msisdn});

                            microBundleRequest.setBandId(band_id);

                            MenuItem menuItem = new MenuController().getMenuItem(band_id, optionId_final);

                            microBundleRequest.setOptionId(optionId_final);
                            microBundleRequest.setImsi(imsi);
                            microBundleRequest.setPin(null);
                            microBundleRequest.setSessionId(sessionid);

                            //get the source of this request
                            String src = request.getHeader("X-Real-IP");

                            if (src == null) {
                                src = request.getRemoteAddr();
                            }

                            microBundleRequest.setSourceIp(src);

                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            LOGGER.log(Level.INFO, "excecute: {0}", microBundleRequest);

                            MicroBundleRequestProcessor microBundleRequestProcessor = new MicroBundleRequestProcessor(microBundleRequest, menuItem);
                            microBundleRequestProcessor.run();

                        } catch (MyPakalastBundleException ex) {
                            LOGGER.log(Level.INFO, ex.getLocalizedMessage());
                            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());
                        }
                    });

                } else {
                    //proceed to process Airtel Money Request
                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", msisdn);

                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                    LOGGER.log(Level.INFO, "SUBMIT-TASK-TO-QUEUE | {0}", msisdn);

                    final int optionId_final = (optionId);

                    mes.execute(() -> {
                        try {
                            MicroBundleRequest microBundleRequest = new MicroBundleRequest();

                            //fetch the band_id
                            int band_id = cacheController.fetchSubscriberBand(msisdn);

                            LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, msisdn});

                            microBundleRequest.setBandId(band_id);

                            MenuItem menuItem = new MenuController().getMenuItem(band_id, optionId_final);

                            microBundleRequest.setOptionId(optionId_final);
                            microBundleRequest.setImsi(imsi);
                            microBundleRequest.setPin(input);
                            microBundleRequest.setSessionId(sessionid);

                            //get the source of this request
                            String src = request.getHeader("X-Real-IP");

                            if (src == null) {
                                src = request.getRemoteAddr();
                            }

                            microBundleRequest.setSourceIp(src);

                            microBundleRequest.setServiceClass(cacheController.fetchServiceClass(msisdn));

                            LOGGER.log(Level.INFO, "EXECUTE: {0}", microBundleRequest);

                            MicroBundleRequestProcessor microBundleRequestProcessor = new MicroBundleRequestProcessor(microBundleRequest, menuItem);
                            microBundleRequestProcessor.run();

                        } catch (MyPakalastBundleException ex) {
                            LOGGER.log(Level.INFO, ex.getLocalizedMessage());
                            SMSClient.send_sms(msisdn, ex.getLocalizedMessage());
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

        } finally {
            out.close();
        }
    }

    public void validateBundleSelected(int input) throws MyPakalastBundleException {

        LOGGER.log(Level.INFO, "VALIDATE-BUNDLE-SELECTED");

        if (input < 1 || input > 3) {
            LOGGER.log(Level.INFO, "INVALID-CHOICE-SELECTED >> {0}", input);
            throw new MyPakalastBundleException("Invalid choice please choose between options 1-3.", 126);
        }
    }

}
