package com.saucelabs.saucerest;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.rmi.UnexpectedException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.saucelabs.saucerest.DataCenter.US;

/**
 * Simple Java API that invokes the Sauce REST API.  The full list of the Sauce REST API
 * functionality is available from
 * <a href="https://docs.saucelabs.com/reference/rest-api">https://docs.saucelabs.com/reference/rest-api</a>.
 *
 * @author Ross Rowe
 */
public class SauceREST implements Serializable {

    /**
     * Logger instance.
     */
    private static final Logger logger = Logger.getLogger(SauceREST.class.getName());
    /**
     * 10 seconds in milliseconds.
     */
    private static final long HTTP_READ_TIMEOUT_SECONDS = TimeUnit.SECONDS.toMillis(System.getenv("SAUCE_HTTP_READ_TIMEOUT_SECONDS") != null ? Integer.parseInt(System.getenv("SAUCE_HTTP_READ_TIMEOUT_SECONDS")) : 10);
    /**
     * 10 seconds in milliseconds.
     */
    private static final long HTTP_CONNECT_TIMEOUT_SECONDS = TimeUnit.SECONDS.toMillis(10);
    /**
     * The username to use when performing HTTP requests to the Sauce REST API.
     */
    protected String username;
    /**
     * The access key to use when performing HTTP requests to the Sauce REST API.
     */
    protected String accessKey;

    /**
     * Date format used as part of the file name for downloaded files.
     */
    private static final String DATE_FORMAT = "yyyyMMdd_HHmmSS";

    private static String extraUserAgent = "";

    private String server;
    private String edsServer;
    private String appServer;

    /**
     * Constructs a new instance of the SauceREST class, uses US as the default data center
     *
     * @param username  The username to use when performing HTTP requests to the Sauce REST API
     * @param accessKey The access key to use when performing HTTP requests to the Sauce REST API
     */
    public SauceREST(String username, String accessKey) {
        this(username, accessKey, US);
    }

    /**
     * Constructs a new instance of the SauceREST class, matching the datacenter string to datacenter object
     *
     * @param username   The username to use when performing HTTP requests to the Sauce REST API
     * @param accessKey  The access key to use when performing HTTP requests to the Sauce REST API
     * @param dataCenter the data center to use
     */
    public SauceREST(String username, String accessKey, String dataCenter) {
        this(username, accessKey, DataCenter.fromString(dataCenter));
    }

    /**
     * Constructs a new instance of the SauceREST class.
     *
     * @param username   The username to use when performing HTTP requests to the Sauce REST API
     * @param accessKey  The access key to use when performing HTTP requests to the Sauce REST API
     * @param dataCenter the datacenter to use
     */
    public SauceREST(String username, String accessKey, DataCenter dataCenter) {
        this.username = username;
        this.accessKey = accessKey;
        this.server = buildUrl(dataCenter.server(), "SAUCE_REST_ENDPOINT", "saucerest-java.base_url");
        this.edsServer = buildUrl(dataCenter.edsServer(), "SAUCE_REST_EDS_ENDPOINT", "saucerest-java.base_eds_url");
        this.appServer = buildUrl(dataCenter.appServer(), "SAUCE_REST_APP_ENDPOINT", "saucerest-java.base_app_url");
    }

    /**
     * Build URL with environment variable, or system property, or default URL.
     *
     * @param defaultUrl         default URL if no URL is found in environment variables and system properties
     * @param envVarName         the name of the environment variable that may contain URL
     * @param systemPropertyName the name of the system property that may contain URL
     * @return URL to use
     */
    private String buildUrl(String defaultUrl, String envVarName, String systemPropertyName) {
        String envVar = System.getenv(envVarName);
        return envVar != null ? envVar : System.getProperty(systemPropertyName, defaultUrl);
    }

    public static String getExtraUserAgent() {
        return extraUserAgent;
    }

    public static void setExtraUserAgent(String extraUserAgent) {
        SauceREST.extraUserAgent = extraUserAgent;
    }

    /**
     * Returns username assigned to this interface
     *
     * @return Returns username assigned to this interface
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Returns server assigned to this interface
     *
     * @return Returns server assigned to this interface
     */
    public String getServer() {
        return this.server;
    }

    /**
     * Returns eds server assigned to this interface
     *
     * @return Returns eds server assigned to this interface
     */
    public String getEdsServer() {
        return this.edsServer;
    }

    /**
     * Returns app server assigned to this interface
     *
     * @return Returns app server assigned to this interface
     */
    public String getAppServer() {
        return this.appServer;
    }

    /**
     * Build the url to be
     *
     * @param endpoint Endpoint url, example "info/platforms/appium"
     * @return URL to use in direct fetch functions
     */
    protected URL buildURL(String endpoint) {
        try {
            return new URL(new URL(this.server), "/rest/" + endpoint);
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Error constructing Sauce URL", e);
            return null;
        }
    }

    /**
     * Build URLs for the EDS server
     *
     * @param endpoint Endpoint url, example "info/platforms/appium"
     * @return URL to use in direct fetch functions
     */
    protected URL buildEDSURL(String endpoint) {
        try {
            return new URL(new URL(this.edsServer), endpoint);
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Error constructing Sauce EDS URL", e);
            return null;
        }
    }

    protected String getUserAgent() {
        String userAgent = "SauceREST/" + BuildUtils.getCurrentVersion();
        if (!"".equals(getExtraUserAgent())) {
            userAgent = userAgent + " " + getExtraUserAgent();
        }
        logger.log(Level.FINEST, "userAgent is set to " + userAgent);
        return userAgent;
    }

    public String doJSONPOST(URL url, JSONObject body) throws SauceException {
        HttpURLConnection postBack = null;
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;

        try {
            postBack = openConnection(url);
            postBack.setRequestProperty("User-Agent", this.getUserAgent());

            if (postBack instanceof HttpsURLConnection) {
                SauceSSLSocketFactory factory = new SauceSSLSocketFactory();
                ((HttpsURLConnection) postBack).setSSLSocketFactory(factory);
            }
            postBack.setDoOutput(true);
            postBack.setRequestMethod("POST");
            postBack.setRequestProperty("Content-Type", "application/json");
            addAuthenticationProperty(postBack);

            logger.log(Level.FINE, "POSTing to " + url.toString());
            logger.log(Level.FINE, body.toString(2));   // PrettyPrint JSON with an indent of 2

            postBack.getOutputStream().write(body.toString().getBytes());

            reader = new BufferedReader(new InputStreamReader(postBack.getInputStream()));

            String inputLine;
            logger.log(Level.FINEST, "Building string from response.");
            while ((inputLine = reader.readLine()) != null) {
                logger.log(Level.FINEST, "  " + inputLine);
                builder.append(inputLine);
            }
        } catch (IOException e) {
            try {
                if (postBack.getResponseCode() == 401) {
                    logger.log(Level.SEVERE, "Error POSTing to " + url.toString() + ": Unauthorized (401)");
                    throw new SauceException.NotAuthorized();
                }
            } catch (IOException e1) {
                logger.log(Level.SEVERE, "Error POSTing to " + url.toString() + " and getting status code: ", e);
            }

            logger.log(Level.SEVERE, "Error POSTing to " + url.toString() + ":", e);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.log(Level.SEVERE, "Error POSTing to " + url.toString() + ":", e);
        } finally {
            closeInputStream(postBack);
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing Sauce input stream", e);
            }
        }
        return builder.toString();
    }


    /**
     * Marks a Sauce Job as 'passed'.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     */
    public void jobPassed(String jobId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("passed", true);
        updateJobInfo(jobId, updates);
    }

    /**
     * Marks a Sauce Job as 'failed'.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     */
    public void jobFailed(String jobId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("passed", false);
        updateJobInfo(jobId, updates);
    }

    /**
     * Adds the provided tags to the Sauce Job
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param tags  the tags to be added to the job, provided as a list of strings
     */
    public void addTags(String jobId, List<String> tags) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("tags", tags);
        updateJobInfo(jobId, updates);
    }

    /**
     * Downloads the video for a Sauce Job to the filesystem.  The file will be stored in a directory
     * specified by the <code>location</code> field.
     * <p>
     * Jobs are only available for jobs which finished without a Sauce side error, and for which the 'recordVideo' capability
     * is not set to false.
     * <p>
     * If an IOException is encountered during operation, this method will fail _silently_.  Prefer {@link #downloadVideoOrThrow(String, String)}
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the video should be downloaded to
     * @return True if the video was downloaded successfully; Otherwise false
     */
    public boolean downloadVideo(String jobId, String location) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/video.mp4");
        return saveFile(jobId, location, restEndpoint);
    }

    /**
     * TODO: 2020-02-27 I think this should be called "downloadVideo" and "attemptVideoDownload" should be the silent failure method - Dylan
     * Downloads the video for a Sauce Job to the filesystem.  The file will be stored in a directory
     * specified by the <code>location</code> field.
     * <p>
     * Jobs are only available for jobs which finished without a Sauce side error, and for which the 'recordVideo' capability
     * is not set to false.
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the video should be downloaded to
     * @throws FileNotFoundException                                if the log is missing or doesn't exist
     * @throws com.saucelabs.saucerest.SauceException.NotAuthorized if credentials are wrong or missing
     * @throws IOException                                          if something else goes wrong during asset retrieval
     */
    public void downloadVideoOrThrow(String jobId, String location) throws SauceException.NotAuthorized, IOException {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/video.mp4");
        saveFileOrThrowException(jobId, location, restEndpoint);
    }

    /**
     * Downloads the video for a Sauce Job and returns it.
     * <p>
     * Will probably eat your memory.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return A BufferedInputStream containing the video info
     * @throws IOException if there is a problem fetching the data
     */

    public BufferedInputStream downloadVideo(String jobId) throws IOException {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/video.mp4");
        return downloadFileData(jobId, restEndpoint);
    }

    /**
     * TODO: 2020-02-27 I think this should be called "attemptLogDownload" - Dylan
     * Downloads the log file for a Sauce Job to the filesystem.  The file will be stored in a
     * directory specified by the <code>location</code> field.
     * <p>
     * If an IOException is encountered during operation, this method will fail _silently_.  Prefer {@link #downloadLogOrThrow(String, String)}
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the video should be downloaded to
     * @return True if the Log file downloads successfully; Otherwise false.
     */
    public boolean downloadLog(String jobId, String location) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/selenium-server.log");
        return saveFile(jobId, location, restEndpoint);
    }

    /**
     * TODO: 2020-02-27 I think this should be called "downloadLog" and "attemptLogDownload" should be the silent failure method - Dylan
     * Downloads the log file for a Sauce Job to the filesystem.  The file will be stored in a
     * directory specified by the <code>location</code> field.
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the video should be downloaded to
     * @throws FileNotFoundException                                if the log is missing or doesn't exist
     * @throws com.saucelabs.saucerest.SauceException.NotAuthorized if credentials are wrong or missing
     * @throws IOException                                          if something else goes wrong during asset retrieval
     */
    public void downloadLogOrThrow(String jobId, String location) throws SauceException.NotAuthorized, IOException {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/selenium-server.log");
        saveFileOrThrowException(jobId, location, restEndpoint);
    }

    /**
     * Downloads the log file for a Sauce Job and returns it.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return a BufferedInputStream containing the logfile
     * @throws IOException if there is a problem fetching the file
     */
    public BufferedInputStream downloadJsonLog(String jobId) throws IOException {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/log.json");
        return downloadFileData(jobId, restEndpoint);
    }

    /**
     * Downloads the log file for a Sauce Job to the filesystem.  The file will be stored in
     * a directory specified by the <code>location</code> field.
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the video should be downloaded to
     * @return True if the Log file downloads successfully; Otherwise false.
     */
    public boolean downloadJsonLog(String jobId, String location) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/log.json");
        return saveFile(jobId, location, restEndpoint);
    }

    /**
     * Downloads the log file for a Sauce Job and returns it.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return a BufferedInputStream containing the logfile
     * @throws IOException if there is a problem fetching the file
     */
    public BufferedInputStream downloadLog(String jobId) throws IOException {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/assets/selenium-server.log");
        return downloadFileData(jobId, restEndpoint);
    }

    /**
     * TODO: 2020-02-27 I think this should be renamed "attemptHARDownload" - Dylan
     * Downloads the HAR file for a Sauce Job to the filesystem.  The file will be stored in a
     * directory specified by the <code>location</code> field.
     * <p>
     * This will only work for jobs which support Extended Debugging, which were started with the
     * 'extendedDebugging' capability set to true.
     * <p>
     * If an IOException is encountered during operation, this method will fail _silently_.  Prefer {@link #downloadHAROrThrow(String, String)}
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the HAR file should be downloaded to
     * @return True if the HAR file downloads successfully, otherwise false
     */
    public boolean downloadHAR(String jobId, String location) {
        URL restEndpoint = this.buildEDSURL(jobId + "/network.har");
        return saveFile(jobId, location, restEndpoint);
    }

    /**
     * TODO: 2020-02-27 I think this should be called "downloadHAR" and attemptHARDownload should be the silent failure method - Dylan
     * Downloads the HAR file for a Sauce Job to the filesystem.  The file will be stored in a
     * directory specified by the <code>location</code> field.
     * <p>
     * This will only work for jobs which support Extended Debugging, which were started with the
     * 'extendedDebugging' capability set to true.
     *
     * @param jobId    the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @param location represents the base directory where the HAR file should be downloaded to
     * @throws FileNotFoundException                                When HAR File is unavailable or doesn't exist.
     * @throws com.saucelabs.saucerest.SauceException.NotAuthorized if credentials are wrong or missing
     * @throws IOException                                          if something else goes wrong during asset retrieval
     */
    public void downloadHAROrThrow(String jobId, String location) throws SauceException.NotAuthorized, IOException {
        URL restEndpoint = this.buildEDSURL(jobId + "/network.har");
        saveFileOrThrowException(jobId, location, restEndpoint);
    }

    /**
     * Downloads the HAR file for a Sauce Job.
     * <p>
     * This will only work for jobs which support Extended Debugging, which were started with the
     * 'extendedDebugging' capability set to true.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return A BufferedInputStream containing the HAR data, unparsed
     * @throws IOException if there is a problem fetching the HAR file
     */
    public BufferedInputStream getHARDataStream(String jobId) throws IOException {
        logger.log(Level.FINEST, "getHARDataStream for " + jobId);
        URL restEndpoint = this.buildEDSURL(jobId + "/network.har");
        return downloadFileData(jobId, restEndpoint);
    }

    /**
     * Downloads the HAR file for a Sauce Job, and returns it wrapped in a {@link JSONTokener}.
     * <p>
     * Pass this {@link JSONTokener} to a {@link JSONObject} when you wish to read JSON.  The stream will be read as
     * soon as a {@link JSONObject} is created.
     * <p>
     * This will only work for jobs which support Extended Debugging, which were started with the
     * 'extendedDebugging' capability set to true.
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return A {@link JSONTokener} containing the HAR data, tokenized
     * @throws IOException   if there is a problem fetching the HAR file
     * @throws JSONException if encoding can't be determined or there's an IO problem
     */
    public JSONTokener getHARData(String jobId) throws IOException, JSONException {
        logger.log(Level.FINEST, "getHARData for " + jobId);
        URL restEndpoint = this.buildEDSURL(jobId + "/network.har");

        BufferedInputStream har_stream = downloadFileData(jobId, restEndpoint);
        return new JSONTokener(har_stream);
    }

    /**
     * Returns the HTTP response for invoking https://saucelabs.com/rest/v1/path.
     *
     * @param path path to append to the url
     * @return HTTP response contents
     */
    public String retrieveResults(String path) {
        URL restEndpoint = this.buildURL("v1/" + path);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @param jobId the Sauce Job id to retrieve
     * @return String (in JSON format) representing the details for a Sauce job
     */
    public String getJobInfo(String jobId) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @return String (in JSON format) representing the details for a Sauce job
     */
    public String getFullJobs() {
        return getFullJobs(20);
    }

    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @param limit Number of jobs to return
     * @return String (in JSON format) representing the details for a Sauce job
     */
    public String getFullJobs(int limit) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs?full=true&limit=" + limit);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @return String (in JSON format) representing the jobID for a sauce Job
     */
    public String getJobs() {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs");
        return retrieveResults(restEndpoint);
    }


    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @param limit Number of jobs to return(max of 500)
     * @return String (in JSON format) representing the jobID for a sauce Job
     */
    public String getJobs(int limit) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs?limit=" + limit);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the details for a Sauce job.
     *
     * @param limit Number of jobs to return(max of 500)
     * @param to    value in Epoch time format denoting the time to end the job list searh
     * @param from  value in Epoch time format denoting the time to start the search
     * @return String (in JSON format) representing the jobID for a sauce Job
     */
    public String getJobs(int limit, long to, int from) {
        URL restEndpoint = this.buildURL("v1/" + username + "/jobs?limit=" + limit + "&from=" + to + "&to=" + from);
        return retrieveResults(restEndpoint);
    }

    /**
     * @param restEndpoint the URL to perform a HTTP GET
     * @return Returns the response from invoking a HTTP GET for the restEndpoint
     */
    public String retrieveResults(URL restEndpoint) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {

            HttpURLConnection connection = openConnection(restEndpoint);
            connection.setRequestProperty("User-Agent", this.getUserAgent());

            if (connection instanceof HttpsURLConnection) {
                SauceSSLSocketFactory factory = new SauceSSLSocketFactory();
                ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
            }

            connection.setRequestProperty("charset", "utf-8");
            connection.setDoOutput(true);
            addAuthenticationProperty(connection);

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            while ((inputLine = reader.readLine()) != null) {
                builder.append(inputLine);
            }
        } catch (SocketTimeoutException e) {
            logger.log(Level.SEVERE, "Received a SocketTimeoutException when invoking Sauce REST API, check status.saucelabs.com for network outages", e);
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            logger.log(Level.SEVERE, "Error retrieving Sauce Results", e);
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing Sauce input stream", e);
        }
        return builder.toString();
    }

    /**
     * Returns the result of a HTTP get to the value of the <code>restEndpoint</code> parameter, as a
     * BufferedInputStream suitable for consumption or saving to file.
     *
     * @param jobId        the Sauce Job id
     * @param restEndpoint the URL to perform a HTTP GET
     * @throws FileNotFoundException        if the requested resource is missing
     * @throws SauceException.NotAuthorized if the credentials are wrong
     * @throws IOException                  when something goes wrong fetching the data
     */
    // TODO: Asset fetching can fail just after a test finishes.  Allow for configurable retries.
    private BufferedInputStream downloadFileData(String jobId, URL restEndpoint) throws SauceException.NotAuthorized, IOException {
        logger.log(Level.FINE, "Downloading asset " + restEndpoint.toString() + " For Job " + jobId);
        logger.log(Level.FINEST, "Opening connection for Job " + jobId);
        HttpURLConnection connection = openConnection(restEndpoint);
        connection.setRequestProperty("User-Agent", this.getUserAgent());

        connection.setDoOutput(true);
        connection.setRequestMethod("GET");
        addAuthenticationProperty(connection);

        Integer responseCode = connection.getResponseCode();
        logger.log(Level.FINEST, responseCode.toString() + " - " + restEndpoint + " for: " + jobId);

        switch (responseCode) {
            case 404:
                String error = ErrorExplainers.resourceMissing();

                String path = restEndpoint.getPath();
                if (path.endsWith("mp4")) {
                    error = String.join(System.getProperty("line.separator"), error, ErrorExplainers.videoMissing());
                } else if (path.endsWith("har")) {
                    error = String.join(System.getProperty("line.separator"), error, ErrorExplainers.HARMissing());
                }

                throw new FileNotFoundException(error);

            case 401:
                String errorReasons = new String();
                if (username == null || username.isEmpty()) {
                    errorReasons = String.join(System.getProperty("line.separator"), "Your username is empty or blank.");
                }

                if (accessKey == null || accessKey.isEmpty()) {
                    errorReasons = String.join(System.getProperty("line.separator"), "Your access key is empty or blank.");
                }

                if (!errorReasons.isEmpty()) {
                    errorReasons = (String.join(System.getProperty("line.separator"), errorReasons, ErrorExplainers.missingCreds()));
                } else {
                    errorReasons = ErrorExplainers.incorrectCreds(username, accessKey);
                }

                throw new SauceException.NotAuthorized(errorReasons);
        }

        logger.log(Level.FINEST, "Obtaining input stream for request issued for Job " + jobId);
        InputStream stream = connection.getInputStream();
        return new BufferedInputStream(stream);
    }

    /**
     * Stores the result of a HTTP GET to the value of the <code>restEndpoint</code> parameter, saving
     * the resulting file to the directory defined by the <code>location</code> parameter.
     * <p>
     * If an IOException is thrown during this process, this method will fail _silently_ (although it will record the error
     * at Level.WARNING.  Use {@link #saveFileOrThrowException(String, String, URL)} to fail with an exception.
     *
     * @param jobId        the Sauce Job id
     * @param location     represents the location that the result file should be stored in
     * @param restEndpoint the URL to perform a HTTP GET
     * @return Whether the request successfully fetched a resource or not
     */
    private boolean saveFile(String jobId, String location, URL restEndpoint) {
        try {
            saveFileOrThrowException(jobId, location, restEndpoint);
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error downloading Sauce Results", e);
            return false;
        }
    }

    private void saveFileOrThrowException(String jobId, String location, URL restEndpoint) throws SauceException.NotAuthorized, IOException {
        String jobAndAsset = restEndpoint.toString() + " for Job " + jobId;
        logger.log(Level.FINEST, "Attempting to save asset " + jobAndAsset + " to " + location);

        BufferedInputStream in = downloadFileData(jobId, restEndpoint);
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        String saveName = jobId + format.format(new Date());
        if (restEndpoint.getPath().endsWith(".mp4")) {
            saveName = saveName + ".mp4";
        } else if (restEndpoint.getPath().endsWith(".har")) {
            saveName = saveName + ".har";
        } else if (restEndpoint.getPath().endsWith(".json")) {
            saveName = saveName + ".json";
        } else {
            saveName = saveName + ".log";
        }

        logger.log(Level.FINEST, "Saving " + jobAndAsset + " as " + saveName);
        FileOutputStream file = new FileOutputStream(new File(location, saveName));
        try (BufferedOutputStream out = new BufferedOutputStream(file)) {
            int i;
            while ((i = in.read()) != -1) {
                out.write(i);
            }
            out.flush();
        }
    }

    /**
     * Adds an Authorization request property to the HTTP connection.
     *
     * @param connection HttpURLConnection instance which represents the current HTTP request
     */
    protected void addAuthenticationProperty(HttpURLConnection connection) {
        if (username != null && accessKey != null) {
            String auth = encodeAuthentication();
            logger.log(Level.FINE, "Encoded Authorization: " + auth);
            connection.setRequestProperty("Authorization", auth);
        }
    }

    /**
     * Invokes the Sauce REST API to update the details of a Sauce job, using the details included in
     * the <code>updates</code> parameter.
     *
     * @param jobId   the Sauce job id to update
     * @param updates Map of attributes to update
     */
    public void updateJobInfo(String jobId, Map<String, Object> updates) {
        HttpURLConnection postBack = null;
        try {
            URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId);
            postBack = openConnection(restEndpoint);
            postBack.setRequestProperty("User-Agent", this.getUserAgent());
            postBack.setDoOutput(true);
            postBack.setRequestMethod("PUT");
            addAuthenticationProperty(postBack);
            postBack.getOutputStream().write(new JSONObject(updates).toString().getBytes());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error updating Sauce Results", e);
        }

        closeInputStream(postBack);
    }

    /**
     * Invokes the Sauce REST API to stop a running job.
     *
     * @param jobId the Sauce Job id
     */
    public void stopJob(String jobId) {
        HttpURLConnection postBack = null;

        try {
            URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId + "/stop");

            postBack = openConnection(restEndpoint);
            postBack.setRequestProperty("User-Agent", this.getUserAgent());
            postBack.setDoOutput(true);
            postBack.setRequestMethod("PUT");
            addAuthenticationProperty(postBack);
            postBack.getOutputStream().write("".getBytes());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error stopping Sauce Job", e);
        }

        closeInputStream(postBack);
    }

    /**
     * Invokes the Sauce REST API to delete a completed job from Sauce.
     *
     * @param jobId the Sauce Job id
     */
    public void deleteJob(String jobId) {
        HttpURLConnection postBack = null;

        try {
            URL restEndpoint = this.buildURL("v1/" + username + "/jobs/" + jobId);

            postBack = openConnection(restEndpoint);
            postBack.setRequestProperty("User-Agent", this.getUserAgent());
            postBack.setDoOutput(true);
            postBack.setRequestMethod("DELETE");
            addAuthenticationProperty(postBack);
            postBack.getOutputStream().write("".getBytes());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error stopping Sauce Job", e);
        }

        closeInputStream(postBack);
    }

    private void closeInputStream(HttpURLConnection connection) {
        try {
            if (connection != null) {
                connection.getInputStream().close();
            }
        } catch (SocketTimeoutException e) {
            logger.log(Level.SEVERE, "Received a SocketTimeoutException when invoking Sauce REST API, check status.saucelabs.com for network outages", e);
        } catch (IOException e) {
            try {
                int responseCode = connection.getResponseCode();
                if (responseCode == 401) {
                    throw new SauceException.NotAuthorized();
                } else if (responseCode == 429) {
                    throw new SauceException.TooManyRequests();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error determining response code", e);
            }
            logger.log(Level.WARNING, "Error closing result stream", e);
        }
    }

    /**
     * Opens a connection to a url.
     *
     * @param url URL to connect to
     * @return HttpURLConnection instance representing the URL connection
     * @throws IOException when a bad url is provided
     */
    public HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection con;
        if ("true".equals(System.getenv("USE_PROXY"))) {
            logger.log(Level.SEVERE, "Using proxy: " + System.getenv("http.proxyHost")
                + System.getenv("http.proxyPort"));

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(System.getenv("http.proxyHost"),
                Integer.parseInt(System.getenv("http.proxyPort"))));
            con = (HttpURLConnection) url.openConnection(proxy);
        } else {
            con = (HttpURLConnection) url.openConnection();
        }
        con.setReadTimeout((int) HTTP_READ_TIMEOUT_SECONDS);
        con.setConnectTimeout((int) HTTP_CONNECT_TIMEOUT_SECONDS);
        return con;
    }

    /**
     * Uploads a file to Sauce storage.
     *
     * @param file the file to upload -param fileName uses file.getName() to store in sauce -param
     *             overwrite set to true
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    public String uploadFile(File file) throws IOException {
        return uploadFile(file, file.getName());
    }

    /**
     * Uploads a file to Sauce storage.
     *
     * @param file     the file to upload
     * @param fileName name of the file in sauce storage -param overwrite set to true
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    public String uploadFile(File file, String fileName) throws IOException {
        return uploadFile(file, fileName, true);
    }

    /**
     * @deprecated use {@link #uploadFile(File, String, boolean)}.
     *
     * Uploads a file to Sauce storage.
     * @param file      the file to upload
     * @param fileName  name of the file in sauce storage
     * @param overwrite boolean flag to overwrite file in sauce storage if it exists
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    @Deprecated
    public String uploadFile(File file, String fileName, Boolean overwrite) throws IOException {
        return uploadFile(file, fileName, overwrite.booleanValue());
    }

    /**
     * Uploads a file to Sauce storage.
     *
     * @param file      the file to upload
     * @param fileName  name of the file in sauce storage
     * @param overwrite boolean flag to overwrite file in sauce storage if it exists
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    public String uploadFile(File file, String fileName, boolean overwrite) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return uploadFile(is, fileName, overwrite);
        }
    }

    /**
     * @deprecated use {@link #uploadFile(InputStream, String, boolean)}.
     * Uploads a file to Sauce storage.
     *
     * @param is        Input stream of the file to be uploaded
     * @param fileName  name of the file in sauce storage
     * @param overwrite boolean flag to overwrite file in sauce storage if it exists
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    @Deprecated
    public String uploadFile(InputStream is, String fileName, Boolean overwrite) throws IOException {
        return uploadFile(is, fileName, overwrite.booleanValue());
    }

    /**
     * Uploads a file to Sauce storage.
     *
     * @param is        Input stream of the file to be uploaded
     * @param fileName  name of the file in sauce storage
     * @param overwrite boolean flag to overwrite file in sauce storage if it exists
     * @return the md5 hash returned by sauce of the file
     * @throws IOException can be thrown when server returns an error (tcp or http status not in the
     *                     200 range)
     */
    public String uploadFile(InputStream is, String fileName, boolean overwrite) throws IOException {
        try {
            URL restEndpoint = this.buildURL("v1/storage/" + username + "/" + fileName + "?overwrite=" + overwrite);

            HttpURLConnection connection = openConnection(restEndpoint);

            if (connection instanceof HttpsURLConnection) {
                SauceSSLSocketFactory factory = new SauceSSLSocketFactory();
                ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
            }

            connection.setRequestProperty("User-Agent", this.getUserAgent());
            addAuthenticationProperty(connection);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Content-Type", "application/octet-stream");

            DataOutputStream oos = new DataOutputStream(connection.getOutputStream());

            int c;
            byte[] buf = new byte[8192];

            while ((c = is.read(buf, 0, buf.length)) > 0) {
                oos.write(buf, 0, c);
                oos.flush();
            }
            oos.close();

            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                builder.append(line);
            }

            JSONObject sauceUploadResponse = new JSONObject(builder.toString());
            if (sauceUploadResponse.has("error")) {
                throw new UnexpectedException("Failed to upload to sauce-storage: "
                    + sauceUploadResponse.getString("error"));
            }
            return sauceUploadResponse.getString("md5");
        } catch (JSONException e) {
            throw new UnexpectedException("Failed to parse json response.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnexpectedException("Failed to get algorithm.", e);
        } catch (KeyManagementException e) {
            throw new UnexpectedException("Failed to get key management.", e);
        }
    }

    /**
     * Generates a link to the job page on Saucelabs.com, which can be accessed without the user's
     * credentials. Auth token is HMAC/MD5 of the job ID with the key &lt;username&gt;:&lt;api key&gt;
     * (see <a href="http://saucelabs.com/docs/integration#public-job-links">http://saucelabs.com/docs/integration#public-job-links</a>).
     *
     * @param jobId the Sauce Job Id, typically equal to the Selenium/WebDriver sessionId
     * @return link to the job page with authorization token
     */
    public String getPublicJobLink(String jobId) {
        try {
            String key = username + ":" + accessKey;
            String auth_token = SecurityUtils.hmacEncode("HmacMD5", jobId, key);
            return server + "jobs/" + jobId + "?auth=" + auth_token;
        } catch (IllegalArgumentException ex) {
            // someone messed up on the algorithm to hmacEncode
            // For available algorithms see {@link http://docs.oracle.com/javase/7/docs/api/javax/crypto/Mac.html}
            // we only want to use 'HmacMD5'
            logger.log(Level.WARNING, "Unable to create an authenticated public link to job:", ex);
            return "";
        }
    }

    /**
     * @return base64 encoded String representing the username/access key
     */
    protected String encodeAuthentication() {
        String auth = username + ":" + accessKey;
        auth = "Basic " + Base64.encodeBase64String(auth.getBytes());
        return auth;
    }

    /**
     * Invokes the Sauce REST API to delete a tunnel.
     *
     * @param tunnelId Identifier of the tunnel to delete
     */
    public void deleteTunnel(String tunnelId) {

        HttpURLConnection connection = null;
        try {
            URL restEndpoint = this.buildURL("v1/" + username + "/tunnels/" + tunnelId);
            connection = openConnection(restEndpoint);
            connection.setRequestProperty("User-Agent", this.getUserAgent());
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");
            addAuthenticationProperty(connection);
            connection.getOutputStream().write("".getBytes());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error stopping Sauce Job", e);
        }

        closeInputStream(connection);
    }

    /**
     * Invokes the Sauce REST API to retrieve the details of the tunnels currently associated with the
     * user.
     *
     * @return String (in JSON format) representing the tunnel information
     */
    public String getTunnels() {
        URL restEndpoint = this.buildURL("v1/" + username + "/tunnels");
        return retrieveResults(restEndpoint);
    }

    /**
     * Invokes the Sauce REST API to retrieve the details of the tunnel.
     *
     * @param tunnelId the Sauce Tunnel id
     * @return String (in JSON format) representing the tunnel information
     */
    public String getTunnelInformation(String tunnelId) {
        URL restEndpoint = this.buildURL("v1/" + username + "/tunnels/" + tunnelId);
        return retrieveResults(restEndpoint);
    }

    /**
     * Invokes the Sauce REST API to retrieve the concurrency details of the user.
     *
     * @return String (in JSON format) representing the concurrency information
     */
    public String getConcurrency() {
        URL restEndpoint = this.buildURL("v1/users/" + username + "/concurrency");
        return retrieveResults(restEndpoint);
    }

    /**
     * Invokes the Sauce REST API to retrieve the activity details of the user.
     *
     * @return String (in JSON format) representing the activity information
     */
    public String getActivity() {
        URL restEndpoint = this.buildURL("v1/" + username + "/activity");
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the stored files list
     *
     * @return String (in JSON format) representing the stored files list
     */
    public String getStoredFiles() {
        URL restEndpoint = this.buildURL("v1/storage/" + username);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the basic account information
     *
     * @return String (in JSON format) representing the basic account information
     */
    public String getUser() {
        URL restEndpoint = this.buildURL("v1/users/" + username);
        return retrieveResults(restEndpoint);
    }

    /**
     * Returns a String (in JSON format) representing the list of objects describing all the OS and
     * browser platforms currently supported on Sauce Labs. (see <a href="https://docs.saucelabs.com/reference/rest-api/#get-supported-platforms">https://docs.saucelabs.com/reference/rest-api/#get-supported-platforms</a>).
     *
     * @param automationApi the automation API name
     * @return String (in JSON format) representing the supported platforms information
     */
    public String getSupportedPlatforms(String automationApi) {
        URL restEndpoint = this.buildURL("v1/info/platforms/" + automationApi);
        return retrieveResults(restEndpoint);
    }

    /**
     * Retrieve jobs associated with a build
     *
     * @param build Build Id
     * @param limit Max jobs to return
     * @return String (in JSON format) representing jobs associated with a build
     */
    public String getBuildFullJobs(String build, int limit) {
        URL restEndpoint = this.buildURL(
            "v1/" + this.username + "/build/" + build + "/jobs?full=1" +
                (limit == 0 ? "" : "&limit=" + limit)
        );
        return retrieveResults(restEndpoint);
    }

    public String getBuildFullJobs(String build) {
        return getBuildFullJobs(build, 0);
    }

    /**
     * Retrieve build info
     *
     * @param build Build name
     * @return String (in JSON format) representing the build
     */
    public String getBuild(String build) {
        URL restEndpoint = this.buildURL(
            "v1/" + this.username + "/builds/" + build); // yes, this goes to builds instead of build like the above
        return retrieveResults(restEndpoint);
    }

    /**
     * Record CI Usage to Sauce Labs
     *
     * @param platform        Platform string. Such as "jenkins", "bamboo", "teamcity"
     * @param platformVersion Version string. Such as "1.1.1"
     * @return if it was a success or not
     */
    public boolean recordCI(String platform, String platformVersion) {
        URL restEndpoint = this.buildURL("v1/stats/ci");
        JSONObject obj = new JSONObject();
        try {
            obj.put("platform", platform);
            obj.put("platform_version", platformVersion);
        } catch (JSONException e) {
            // JSONException - If the key is null.
            logger.log(Level.SEVERE, "Error attempting to craft json:", e);
            return false;
        }

        try {
            doJSONPOST(restEndpoint, obj);
        } catch (SauceException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SauceREST)) {
            return super.equals(obj);
        }
        SauceREST sauceobj = (SauceREST) obj;
        return Objects.equals(sauceobj.username, this.username) &&
            Objects.equals(sauceobj.accessKey, this.accessKey) &&
            Objects.equals(sauceobj.server, this.server);
    }
}
