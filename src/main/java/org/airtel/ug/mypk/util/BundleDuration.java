/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.airtel.ug.mypk.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 *
 * @author admin
 */
public class BundleDuration {
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    
    
    private final String startDateFormat, endDateFormat;
    private final Date endDate, startDate;
    /**
     *
     * @param duration_hours the amount of time for the Product to last
     */
    public BundleDuration(int duration_hours) {
        //get the current Date as object creation
        Calendar instance = Calendar.getInstance();
        startDate = instance.getTime();
        startDateFormat = simpleDateFormat.format(startDate);

        //add the product duration
        instance.add(Calendar.HOUR, duration_hours);
        endDate = instance.getTime();
        endDateFormat = simpleDateFormat.format(endDate);
    }
    
    public String getStartDateAfterHours(int hour) {
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.HOUR, hour);
        return simpleDateFormat.format(instance.getTime());
    }
    
    public String getStartDateAfterMinutes(int minutes) {
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.MINUTE, minutes);
        return simpleDateFormat.format(instance.getTime());
    }
    
    public Date getEndDate() {
        return endDate;
    }
    
    public Date getStartDate() {
        return startDate;
    }
    
    public String getStartDateFormat() {
        return startDateFormat;
    }
    
    public String getEndDateFormat() {
        return endDateFormat;
    }
    
}
