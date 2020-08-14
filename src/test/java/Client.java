
import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;
import org.airtel.ug.mypk.controllers.MenuController;
import org.airtel.ug.mypk.pojo.MenuItem;
import org.airtel.ug.mypk.controllers.CacheController;
import org.airtel.ug.mypk.processors.MicroBundleRequestProcessor;

/**
 * 10-Dec-2018 written in support based on the ARPU for voice subscribers
 *
 * @author benjamin
 */
//@WebServlet(urlPatterns = "/Client")
public class Client extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    @Inject
    private HazelcastInstance client;

    @Resource(lookup = "concurrent/mypakalast")
    private ManagedExecutorService mes;

    @Resource(lookup = "resource/ppimsis")
    private String ppImsis;

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

        //InitialContext ic = null;

        String MSISDN = request.getParameter("MSISDN");
        String SESSIONID = request.getParameter("SESSIONID");
        String TYPE = request.getParameter("TYPE");
        String IMSI = request.getParameter("IMSI");
        String INPUT = request.getParameter("INPUT");

        CacheController microBundleHzClient = new CacheController();
        try {

            LOGGER.log(Level.INFO, "SESSIONID >>> {0} | {1}", new Object[]{SESSIONID, MSISDN});
            LOGGER.log(Level.INFO, "INPUT >>> {0} | {1}", new Object[]{INPUT.replaceAll("\\S", "*"), MSISDN});
            LOGGER.log(Level.INFO, "IMSI >>> {0} | {1}", new Object[]{IMSI, MSISDN});
            LOGGER.log(Level.INFO, "TYPE >>> {0} | {1}", new Object[]{TYPE, MSISDN});

            //************************************************************
            //retrive post-paid imsis
            //ic = new InitialContext();
            //String ppImsis = (String) ic.lookup("resource/ppimsis");

            String[] imsi_list = ppImsis.split("\\,");

            List<String> PP_IMSI_PREFIXES = Arrays.asList(imsi_list);

            boolean isPostpaid = PP_IMSI_PREFIXES.contains(IMSI.substring(0, 8));

            // THIS SECTION ELIMINATES ALL POSTPAID NUMBERS
            if (isPostpaid) {

                LOGGER.log(Level.INFO, "IMSI IS A PREPAID | {0} ", new Object[]{IMSI});

                out.println("Dear Customer, you are not eligible for this service.");

                return;
            }

            ArrayList<MenuItem> menu;

            //for first time freeflow control request flash a Menu
            if (TYPE.equals("1")) {

                response.setHeader("Cont", "FC");

                LOGGER.log(Level.INFO, "LOOKUP-CUSTOMER-BAND | {0}", MSISDN);

                //get the band for this customer
                int band_id = microBundleHzClient.fetchSubscriberBand(MSISDN);

                LOGGER.log(Level.INFO, "BAND-ID-FOUND: {0} | {1}", new Object[]{band_id, MSISDN});

                menu = new MenuController().getMenuForDisplay(band_id);

                LOGGER.log(Level.INFO, "BUILDING_MENU | {0}", MSISDN);

                out.println("MYPAKALAST");
                out.println("---------------");

                //menu.forEach(out::println);
                int item_no = 1;

                for (MenuItem menuItem : menu) {
                    out.println(item_no + "." + menuItem.getMenuItemName());
                    item_no++;
                }

                LOGGER.log(Level.INFO, "DISPLAY_MENU | {0}", MSISDN);

            } else if (INPUT.equals("#")) {

                LOGGER.log(Level.INFO, "OPTS-TO-TERMINATE-SESSION | {0}", MSISDN);

                microBundleHzClient.clearSessionData(SESSIONID);

                response.setHeader("Cont", "FB");

                out.println("Thank You for Choosing Airtel.");

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

                   // mes.submit(new MicroBundleRequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, null));

                } else {
                    //proceed to process Airtel Money Request
                    response.setHeader("Cont", "FB");

                    LOGGER.log(Level.INFO, "Request-Thread-dispatched-AirtelMoney | {0}", MSISDN);

                    out.println("Your request is being processed. Please wait for a confirmation SMS.");

                   // mes.execute(new MicroBundleRequestProcessor(MSISDN, SESSIONID, optionId, src, IMSI, INPUT));
                }
            }
        } catch (NumberFormatException ex) {

            LOGGER.log(Level.INFO, "INVALID-INPUT | {0}", MSISDN);

            response.setHeader("Cont", "FB");

            out.println("Invalid option selected, Please try again.");

            microBundleHzClient.clearSessionData(SESSIONID);

        } catch (MyPakalastBundleException ex) {

            LOGGER.log(Level.INFO, ex.getLocalizedMessage());

            response.setHeader("Cont", "FB");

            out.println(ex.getLocalizedMessage());

            microBundleHzClient.clearSessionData(SESSIONID);

        } catch (IllegalStateException | IndexOutOfBoundsException | NullPointerException | RejectedExecutionException ex) {

            response.setHeader("Cont", "FB");

            out.println("Your request can not be processed at the moment!,Please try again later.");

            microBundleHzClient.clearSessionData(SESSIONID);

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        } finally {
            out.close();
        }
    }//end of process request

    public void validateBundleSelected(int input) throws MyPakalastBundleException {

        LOGGER.log(Level.INFO, "VALIDATE-BUNDLE-SELECTED");

        if (input < 1 || input > 3) {
            LOGGER.log(Level.INFO, "INVALID-CHOICE-SELECTED >> {0}", input);
            throw new MyPakalastBundleException("Invalid choice please choose between options 1-3.",123);
        }
    }

    @PreDestroy
    public void close() {

        LOGGER.log(Level.INFO, "SHUT-DOWN-HZ-INSTANCE");

        client.shutdown();
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
