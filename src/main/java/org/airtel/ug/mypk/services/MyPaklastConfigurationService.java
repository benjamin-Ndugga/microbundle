package org.airtel.ug.mypk.services;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import org.airtel.ug.mypk.controllers.CacheController;

/**
 *
 * @author Benjamin E Ndugga
 */
@Path("/cache")
public class MyPaklastConfigurationService {

    @Inject
    private CacheController cacheController;

    @POST
    @Path("/flush")
    public String flushCache(@PathParam("msisdn") String msisdn) {

        cacheController.flushCache();

        return "Done flushing cache";
    }

    @POST
    @Path("/flush/{msisdn}")
    public String removeNumberFromCache(@PathParam("msisdn") String msisdn) {

        Integer i = cacheController.flushNumberFromCache(msisdn);

        return (i == null ? "There is no Band Cached for the number " + msisdn : "Removed Cached entry <" + msisdn + "," + i + ">");
    }

    @POST
    @Path("/put/{msisdn}/{band}")
    public String addNumberToCache(@PathParam("msisdn") String msisdn, @PathParam("band") String band) {

        Integer i = cacheController.addNumberToCache(msisdn, band);

        return (i == null ? "Added new entry to cache <" + msisdn + "," + band + ">" : "Replaced entry to <" + msisdn + "," + band + ">");
    }

    @GET
    @Path("/fetch/{msisdn}")
    public String fetchCurrentBand(@PathParam("msisdn") String msisdn) {

        return "Current Band: " + cacheController.fetchSubscriberBand(msisdn);

    }
}
