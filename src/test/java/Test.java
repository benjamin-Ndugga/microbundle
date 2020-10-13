
import org.airtel.ug.mypk.controllers.MicroBundleBaseProcessor;
import org.airtel.ug.mypk.exceptions.MyPakalastBundleException;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author 2308156
 */
public class Test {

    public static void main(String[] args) throws MyPakalastBundleException {

        //nteger x = 9;
        //System.out.println(x.toString());
        
        String val = "118030955";
        
        if (!(val.equals(MicroBundleBaseProcessor.OCS_ALREADY_PROCESSED_CODE) || val.equals(MicroBundleBaseProcessor.OCS_SUCCESS_CODE))) {
            System.out.println("send retry");
        } else {
            System.out.println("ignore");
        }
    }

    private static void validateBillingOption(Integer input) throws MyPakalastBundleException {
        if (!input.equals(1) || !input.equals(2)) {
            throw new MyPakalastBundleException("Invalid billing option selected please choose either 1 or 2.");
        }
    }

}
