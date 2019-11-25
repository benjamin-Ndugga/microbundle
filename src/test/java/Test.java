
import java.util.UUID;
import org.airtel.ug.mypk.util.MyPakalastBundleException;

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

        Integer x = 9;

        System.out.println(x.toString());
    }

    private static void validateBillingOption(Integer input) throws MyPakalastBundleException {
        if (!input.equals(1) || !input.equals(2)) {
            throw new MyPakalastBundleException("Invalid billing option selected please choose either 1 or 2.");
        }
    }

}
