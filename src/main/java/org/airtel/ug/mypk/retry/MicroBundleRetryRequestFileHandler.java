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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 *
 * @author Benjamin E Ndugga
 */
@ApplicationScoped
public class MicroBundleRetryRequestFileHandler {

    private static final Logger LOGGER = Logger.getLogger(MicroBundleRetryRequestFileHandler.class.getName());

    private static String FILE_EXTENSION = "-mypakalast.ser";
    private static final String RETRY_FILE_PATH = "/u01/retry/mypakalast/";
    private int FILE_AGE_IN_MINS = 2;
    private int MAX_FILE_COUNT = 5;

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

        LOGGER.log(Level.INFO, "DELETING-FILE: {0}", fileName + FILE_EXTENSION);

        boolean b = new File(RETRY_FILE_PATH + fileName + FILE_EXTENSION).delete();

        LOGGER.log(Level.INFO, "FILE-DELETED: {0}", b);
    }

    /**
     *
     * @param retryRequest
     */
    public void writeRetryTransaction(MicroBundleRetryRequest retryRequest) {

        LOGGER.log(Level.INFO, "WRITING-FILE-PATH: " + RETRY_FILE_PATH);
        LOGGER.log(Level.INFO, "RETRY-CONTENT: {0}", retryRequest);

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {

            fos = new FileOutputStream(new File(RETRY_FILE_PATH + retryRequest.getExternalId() + FILE_EXTENSION));
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

            logRetryRequestToDB(retryRequest);
        }
    }

    /**
     *
     * @return list of files that are to be processed
     */
    public List<MicroBundleRetryRequest> readRetryTransactions() {

        checkConfigs();

        LOGGER.log(Level.INFO, "READING-FILE-PATH: " + RETRY_FILE_PATH);

        try (Stream<Path> filesStream = Files.list(Paths.get(RETRY_FILE_PATH))) {

            List<MicroBundleRetryRequest> list = filesStream
                    .filter((Path path) -> {
                        try {
                            //get all files that have aged out
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

                    //compute the file age
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                    Instant lastModifiedTime = attributes.lastModifiedTime().toInstant();
                    Instant currentTime = Instant.now();
                    long file_age = Duration.between(lastModifiedTime, currentTime).toMinutes();

                    //parse the file into a java object
                    fi = new FileInputStream(new File(RETRY_FILE_PATH + path.getFileName()));
                    oi = new ObjectInputStream(fi);

                    // Read objects
                    MicroBundleRetryRequest retryRequest = (MicroBundleRetryRequest) oi.readObject();
                    retryRequest.setFile_age(file_age);

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
            }).sorted(Comparator.comparingLong(MicroBundleRetryRequest::getFile_age).reversed())
                    .limit(MAX_FILE_COUNT)
                    .collect(Collectors.toList());

            LOGGER.log(Level.INFO, "TOTAL-FILES-TO-PROCESS: {0} ", list.size());

            return list;
        } catch (IOException ex) {

            LOGGER.log(Level.SEVERE, null, ex);

            return null;

        }
    }

    private void checkConfigs() {
        InitialContext ic = null;
        try {

            LOGGER.log(Level.INFO, "LOAD-RETRY-CONFIGS");

            ic = new InitialContext();
            FILE_AGE_IN_MINS = (Integer) ic.lookup("resource/retry/fileagemins");
            MAX_FILE_COUNT = (Integer) ic.lookup("resource/retry/maxfilecount");

            LOGGER.log(Level.INFO, "SET-FILE-AGE {0} mins.", FILE_AGE_IN_MINS);
            LOGGER.log(Level.INFO, "SET-MAX-FILE-COUNT {0}", MAX_FILE_COUNT);

        } catch (NamingException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            try {
                if (ic != null) {
                    ic.close();
                }
            } catch (NamingException ex) {
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }

    private void logRetryRequestToDB(MicroBundleRetryRequest retryRequest) {
        Connection connection = null;
        InitialContext ic = null;

        LOGGER.log(Level.INFO, "LOG-RETRY-ENTRY {0}", retryRequest);
        try {

            ic = new InitialContext();
            DataSource dataSource = (DataSource) ic.lookup("KIKADB");
            connection = (Connection) dataSource.getConnection();

            PreparedStatement statement = connection.prepareStatement("INSERT INTO AIRTEL_AUTO_SETTLEMENTS ("
                    + "MSISDN,"
                    + "SESSION_ID,"
                    + "EXTERNAL_ID,"
                    + "BUNDLE_NAME,"
                    + "RETRIES_DONE,"
                    + "PROCESSING_NODE) "
                    + "VALUES (?,?,?,?,?,?)");

            statement.setString(1, retryRequest.getMsisdn());
            statement.setString(2, retryRequest.getSessionId());
            statement.setString(3, retryRequest.getExternalId());
            statement.setString(4, retryRequest.getBundleName());
            statement.setInt(5, retryRequest.getCurrentRetryCount()-1);//less the current reties as the 1st time is not counted
            statement.setString(6, retryRequest.getProcessing_node());

            int i = statement.executeUpdate();

            LOGGER.log(Level.INFO, "LOG-RETRY-ENTRY-RESPONSE: {0}", i);

            connection.commit();

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);

        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
                if (ic != null) {
                    ic.close();
                }
            } catch (SQLException | NamingException ex) {
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }

    public void deleteRetryRequest(MicroBundleRetryRequest retryRequest) {
        Connection connection = null;
        InitialContext ic = null;
        try {
            LOGGER.log(Level.INFO, "DELETE-DB-RECORD | {0}", retryRequest.getMsisdn());

            ic = new InitialContext();
            DataSource dataSource = (DataSource) ic.lookup("KIKADB");
            connection = (Connection) dataSource.getConnection();

            PreparedStatement statement = connection.prepareStatement("DELETE FROM AIRTEL_AUTO_SETTLEMENTS WHERE EXTERNAL_ID = ?");

            statement.setString(1, retryRequest.getExternalId());

            int i = statement.executeUpdate();

            LOGGER.log(Level.INFO, "DELETE-DB-RECORD | {0}", retryRequest.getMsisdn());

            connection.commit();

        } catch (SQLException | NamingException ex) {
            LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
                if (ic != null) {
                    ic.close();
                }
            } catch (SQLException | NamingException ex) {
                LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            }
        }
    }

}
