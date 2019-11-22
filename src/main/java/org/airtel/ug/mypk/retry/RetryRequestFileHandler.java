package org.airtel.ug.mypk.retry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin E Ndugga
 */
public class RetryRequestFileHandler {

    private static final Logger LOGGER = Logger.getLogger("RETRY-FILE-HANDLER");
    private static final String RETRY_FILE_PATH = "/u01/retry/mypakalast/";
    private static final int FILE_AGE_IN_MINS = 2;
    private static final int MAX_FILE_COUNT = 100;

    static {
        //check if this folder has been created
        if (!new File(RETRY_FILE_PATH).exists()) {
            try {
                Files.createDirectories(Paths.get(RETRY_FILE_PATH));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * deletes the retry file from the file system
     *
     * @param fileName
     */
    public void deletRetryFile(String fileName) {

        LOGGER.log(Level.INFO, "DELETING-FILE: {0}", fileName);

        boolean b = new File(RETRY_FILE_PATH + fileName).delete();

        LOGGER.log(Level.INFO, "FILE-DELETED: {0}", b);
    }

    /**
     *
     * @param retryRequest
     */
    public void writeRetryTransaction(RetryRequest retryRequest) {

        LOGGER.log(Level.INFO, "WRITING-FILE-PATH: " + RETRY_FILE_PATH);
        LOGGER.log(Level.INFO, "RETRY-CONTENT: {0}", retryRequest);

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {

            fos = new FileOutputStream(new File(RETRY_FILE_PATH + retryRequest.getExternalId() + ".ser"));
            oos = new ObjectOutputStream(fos);

            // Write objects to file
            oos.writeObject(retryRequest);

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage() + " | " + retryRequest.getMsisdn(), ex);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }

                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     *
     * @return list of files that are to be processed
     */
    public List<RetryRequest> readRetryTransactions() {

        LOGGER.log(Level.INFO, "READING-FILE-PATH: " + RETRY_FILE_PATH);

        try (Stream<Path> filesStream = Files.list(Paths.get(RETRY_FILE_PATH))) {

            List<RetryRequest> list = filesStream
                    .filter((Path path) -> {
                        try {
                            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);

                            Instant lastModifiedTime = attributes.lastModifiedTime().toInstant();
                            Instant currentTime = Instant.now();

                            long diffInMins = Duration.between(lastModifiedTime, currentTime).toMinutes();

                            LOGGER.log(Level.INFO, "{0} {1}mins. OLD", new Object[]{path.getFileName(), diffInMins});

                            return diffInMins >= FILE_AGE_IN_MINS;

                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                            return false;
                        }
                    }).map((Path path) -> {

                FileInputStream fi = null;
                ObjectInputStream oi = null;
                try {

                    fi = new FileInputStream(new File(RETRY_FILE_PATH + path.getFileName()));
                    oi = new ObjectInputStream(fi);

                    // Read objects
                    RetryRequest retryRequest = (RetryRequest) oi.readObject();

                    LOGGER.log(Level.INFO, "READ-RETRY-OBJECT: {0}", retryRequest.toString());

                    return retryRequest;

                } catch (IOException | ClassCastException | ClassNotFoundException ex) {

                    LOGGER.log(Level.SEVERE, null, ex);

                    return null;

                } finally {
                    try {
                        if (oi != null) {
                            oi.close();
                        }
                        if (fi != null) {
                            fi.close();
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }).limit(MAX_FILE_COUNT)
                    .collect(Collectors.toList());

            LOGGER.log(Level.INFO, "TOTAL-FILES-TO-PROCESS: {0} ", list.size());

            return list;
        } catch (IOException ex) {

            LOGGER.log(Level.SEVERE, null, ex);

            return null;

        }
    }
}
