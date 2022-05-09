package com.tanodxyz.gdownload.connection;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;

import kotlin.Pair;

/**
 * This class's general contract is to connect to remote resources
 */
public abstract class URLConnectionHandler {
    /**
     * Indicates whether remote resource/server accept partial or Ranged requests
     */
    protected boolean acceptRanges = false;
    /**
     * Optional Hash if present for the resource
     */
    protected String md5Hash = "";

    /**
     * Content length of the resource. if it is not determined it will be -1.
     */
    protected long contentLength = -1L;

    /**
     * @see HttpURLConnection#setReadTimeout(int)
     */
    protected int readTimeOut = 0;

    /**
     * @see HttpURLConnection#setConnectTimeout(int)
     */
    protected int connectionTimeOut = 0;

    /**
     * Http method.
     */
    protected String method = "GET";

    /**
     * @see HttpURLConnection#setUseCaches(boolean)
     */
    protected boolean useCache = true;
    /**
     * @see HttpURLConnection#setFollowRedirects(boolean)
     */
    protected boolean followRedirects = true;
    /**
     * @see HttpURLConnection#getResponseCode()
     */
    protected int responseCode = -1;

    /**
     * @see HttpURLConnection#getResponseMessage()
     */
    protected String responseMessage = "";

    /**
     * Return the response error message.
     */
    protected String responseError = "";
    /**
     * Url or address of the remote resource
     */
    protected String url = "";

    /**
     * Request Headers - will be sent with each connection request.
     */
    protected @NonNull
    HashMap<String, String> requestHeaders = new HashMap<>();

    /**
     * set of header that remote server will return upon connecting to.
     */
    protected @NonNull
    HashMap<String, List<String>> responseHeaders = new HashMap<>();

    /**
     * Make a connection to remote resource specified by url.
     *
     * @param url the address of the remote resource
     * @return Remote Connection encapsulating connection meta
     * @throws IOException if failed to connect for some reasons
     */
    public abstract RemoteConnection makeConnection(@NonNull String url) throws IOException;

    /**
     * Make a connection to remote resource with optional parameters for specifying byte ranges.
     *
     * @param url          the address of the remote resource
     * @param retriesCount number of retries in case of connection failure
     * @param startRange   it is the starting byte in the content Length.
     * @param endRange     it is the ending byte in the content Length of the remote resource.
     * @param downloaded   an optional parameter to specify the downloaded or already read bytes
     *                     while starting from start Range.
     * @return The Pair consists of Exception and RemoteConnection. Both return objects are inverse of
     * each other. if there is an exception there would be null Remote Connection Object.
     */
    public abstract Pair<Exception, RemoteConnection> makeConnection(@NonNull String url, int retriesCount, long startRange, long endRange, long downloaded);

    public void reset() {
        acceptRanges = false;
        md5Hash = "";
        contentLength = -1;
        readTimeOut = 0;
        connectionTimeOut = 0;
        method = "GET";
        useCache = true;
        followRedirects = true;
        responseCode = -1;
        responseMessage = "";
        responseError = "";
        url = "";
        requestHeaders.clear();
        responseHeaders.clear();
    }

    /**
     * Reconnect to the already specified Url. {@link URLConnectionHandler#url}
     *
     * @return the Remote Connection Object
     * @throws IOException in case of any exception occured
     */
    public RemoteConnection reconnect() throws IOException {
        return makeConnection(this.url);
    }

    /**
     * Adds the byte range header to the request headers.
     *
     * @param start      starting byte in the content length of the resource
     * @param end        ending byte in the content length of the resource
     * @param downloaded number of bytes read starting from @param start
     * @return whether byte range request can be made with specified arguments
     */
    public boolean addByteRangeHeader(long start, long end, long downloaded) {
        final boolean canMakeRangeRequest = (end > (start + downloaded));
        if (canMakeRangeRequest) {
            requestHeaders.put("Range", "bytes=" + (start + downloaded) + "-" + end);
        }
        return canMakeRangeRequest;
    }
}
