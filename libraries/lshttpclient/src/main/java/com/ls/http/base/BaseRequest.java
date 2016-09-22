/*
 * The MIT License (MIT)
 *  Copyright (c) 2014 Lemberg Solutions Limited
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 */

package com.ls.http.base;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.RequestFuture;
import com.ls.http.base.handler.Handler;
import com.ls.util.L;

import org.apache.http.Header;

import android.net.Uri;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BaseRequest extends Request<ResponseData> {

    protected static String ACCEPT_HEADER_KEY = "Accept";

    public enum RequestMethod {
        GET(Method.GET), POST(Method.POST), PATCH(Method.PATCH), DELETE(Method.DELETE), PUT(Method.PUT), HEAD(Method.HEAD), OPTIONS(Method.OPTIONS), TRACE(Method.TRACE);
        final private int methodCode;

        RequestMethod(int theCode) {
            this.methodCode = theCode;
        }
    }

    public enum RequestFormat {
        JSON, XML, JSON_HAL, TEXT,
        /**
         * Note: Multipart entity serializer is checking if non-transient field implements {@link com.ls.http.base.handler.multipart.IMultiPartEntityPart} interface if so
         * - {@link com.ls.http.base.handler.multipart.IMultiPartEntityPart@getContentBody()} method is called and `toString` otherwise
         */
        MULTIPART;

        private ResponseFormat toResponse() {
            switch (this) {
                case MULTIPART:
                    L.e("Multipart response isn't supported. Using JSON. You can setup custom response type, using RequestOptions");
                    return ResponseFormat.JSON;
                default:
                    return ResponseFormat.valueOf(this.name());
            }
        }
    }

    ;

    public enum ResponseFormat {
        JSON, XML, JSON_HAL, TEXT, BYTE, IMAGE
    }

    private final RequestFormat requestFormat;
    private final ResponseFormat responseFormat;

    private String defaultCharset;


    private Map<String, String> requestHeaders;
    private Map<String, String> postParameters;
    private Map<String, Object> getParameters;
    private Object objectToPost;

    //Do not use during comparison

    private final RequestFuture<ResponseData> syncLock;
    private final Object responseClasSpecifier;
    private final Object errorResponseClasSpecifier;
    private RequestHandler requestHandler;
    private ResponseHandler responseHandler;
    private ResponseData result;
    private OnResponseListener responseListener;
    private boolean smartComparisonEnabled = false;

    /**
     * @param requestConfig Additional request configuration entity, used to provide some additional parameters
     */
    public BaseRequest(RequestMethod requestMethod, String requestUrl, RequestConfig requestConfig) {
        this(requestMethod, requestUrl, requestConfig, getRequestLock());
    }

    /**
     * @param requestConfig Additional request configuration entity, used to provide some additional parameters
     * @param lock          autogenerated object used to perform synchronized requests
     */
    protected BaseRequest(RequestMethod requestMethod, String requestUrl, RequestConfig requestConfig, RequestFuture<ResponseData> lock) {
        super(requestMethod.methodCode, requestUrl, lock);
        this.requestFormat = requestConfig.getRequestFormat();

        ResponseFormat responseFormatL = requestConfig.getResponseFormat();
        if (responseFormatL == null) {
            responseFormatL = this.requestFormat.toResponse();
        }
        this.responseFormat = responseFormatL;
        this.syncLock = lock;
        this.requestHandler = Handler.getRequestHandlerForFormat(this.requestFormat);
        this.responseHandler = Handler.getResponseHandlerForFormat(this.responseFormat);
        this.initRequestHeaders();
        this.responseClasSpecifier = requestConfig.getResponseClassSpecifier();
        this.errorResponseClasSpecifier = requestConfig.getErrorResponseClassSpecifier();
        this.result = new ResponseData();
    }


    public Object getResponseClasSpecifier() {
        return responseClasSpecifier;
    }

    private void initRequestHeaders() {
        this.requestHeaders = new HashMap<String, String>();

        String acceptValueType = this.responseHandler.getAcceptValueType();
        if (!TextUtils.isEmpty(acceptValueType)) {
            this.addRequestHeader(ACCEPT_HEADER_KEY, acceptValueType);
        }
    }

    public ResponseData performRequest(boolean synchronous, RequestQueue theQueque) {
        theQueque.add(this);
        if (synchronous) {
            try {
                this.syncLock.get(); // this call will block
                // thread
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return this.result;
    }

    @Override
    protected Response<ResponseData> parseNetworkResponse(NetworkResponse response) {
        Response<ResponseData> result = this.responseHandler.parseNetworkResponse(response, responseClasSpecifier);
        this.result = result.result;
        return result;
    }

    @Override
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        VolleyError error = super.parseNetworkError(volleyError);
        this.result = new ResponseData();
        if (volleyError.networkResponse != null) {
            this.result.headers = new HashMap<String, String>(volleyError.networkResponse.headers);
            this.result.statusCode = volleyError.networkResponse.statusCode;
            if (this.errorResponseClasSpecifier != null) {
                Response<ResponseData> result = this.responseHandler.parseNetworkResponse(volleyError.networkResponse, errorResponseClasSpecifier);
                this.result.parsedErrorResponse = result.result;
            }
        }
        this.result.error = error;

        return error;
    }

    @Override
    protected void deliverResponse(ResponseData o) {
        this.syncLock.onResponse(result);
        if (this.responseListener != null) {
            this.responseListener.onResponseReceived(result, this);
        }
    }

    @Override
    public void deliverError(VolleyError error) {
        this.result.error = error;
        this.syncLock.onErrorResponse(error);
        if (this.responseListener != null) {
            this.responseListener.onError(result, this);
        }
    }

    public interface OnResponseListener {

        void onResponseReceived(ResponseData data, BaseRequest request);

        void onError(ResponseData data, BaseRequest request);
    }

    public OnResponseListener getResponseListener() {
        return responseListener;
    }

    public void setResponseListener(OnResponseListener responseListener) {
        this.responseListener = responseListener;
    }

    // Header parameters handling

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> result = new HashMap<String, String>();
        result.putAll(super.getHeaders());
        if (this.requestHeaders != null) {
            result.putAll(this.requestHeaders);
        }
        return result;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public void addRequestHeaders(Map<String, String> theRequestHeaders) {
        if(this.requestHeaders == null){
            this.requestHeaders = new HashMap<>();
        }
        this.requestHeaders.putAll(theRequestHeaders);
    }

    public void addRequestHeader(String key, String value) {
        if(this.requestHeaders == null){
            this.requestHeaders = new HashMap<>();
        }
        this.requestHeaders.put(key, value);
    }

    public void addRequestHeader(Header header) {
        addRequestHeader(header.getName(), header.getValue());
    }

    // Post parameters handling

    @Override
    protected Map<String, String> getParams() throws AuthFailureError {
        if (this.postParameters != null) {
            return this.postParameters;
        } else {
            return super.getParams();
        }
    }

    public Map<String, String> getPostParameters() {
        return postParameters;
    }

    public void setPostParameters(Map<String, String> postParameters) {
        this.postParameters = postParameters;
    }

    public void addPostParameters(Map<String, String> postParameters) {
        if (this.postParameters == null) {
            this.postParameters = postParameters;
        } else {
            this.postParameters.putAll(postParameters);
        }
    }

    public void addPostParameter(String key, String value) {
        if (this.postParameters == null) {
            this.postParameters = new HashMap<String, String>();
        }
        if (value == null) {
            this.postParameters.remove(key);
        } else {
            this.postParameters.put(key, value);
        }
    }

    // Post Body handling

    @SuppressWarnings("null")
    @Override
    public byte[] getBody() throws AuthFailureError {
        if (this.objectToPost != null && this.postParameters == null) {

            try {
                return requestHandler.getBody(this.defaultCharset);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return new byte[0];
            }
        } else {
            return super.getBody();
        }
    }

    @SuppressWarnings("null")
    @Override
    public String getBodyContentType() {
        if (this.objectToPost != null) {
            return requestHandler.getBodyContentType(this.defaultCharset);
        }

        return super.getBodyContentType();

    }

    public Object getObjectToPost() {
        return objectToPost;
    }

    public void setObjectToPost(Object objectToPost) {
        this.objectToPost = objectToPost;
        this.requestHandler.setObject(this.objectToPost);
    }

    // Get parameters handling

    @Override
    public String getUrl() {
        if (this.getParameters != null && !this.getParameters.isEmpty()) {
            Uri.Builder builder = Uri.parse(super.getUrl()).buildUpon();
            for (Map.Entry<String, Object> entry : this.getParameters.entrySet()) {
                Object entryValue = entry.getValue();
                String entryKey = entry.getKey();
                if (entryValue == null) {
                    builder.appendQueryParameter(entryKey, null);
                    break;
                }
                if (entryValue instanceof Collection) {
                    Collection items = (Collection) entryValue;
                    for (Object item : items) {
                        if (item == null) {
                            builder.appendQueryParameter(entryKey, null);
                        } else {
                            builder.appendQueryParameter(entryKey, item.toString());
                        }
                    }
                } else {
                    builder.appendQueryParameter(entryKey, entryValue.toString());
                }
            }
            String urlString = builder.build().toString();
            return urlString;
        } else {
            return super.getUrl();
        }
    }

    public Map<String, Object> getGetParameters() {
        return getParameters;
    }

    /**
     * @param getParameters in case if collection is passed as map entry value - all entities will be added under corresponding key. Object.toString will be called otherwise.
     */
    public void setGetParameters(Map<String, Object> getParameters) {
        this.getParameters = getParameters;
    }

    /**
     * @param getParameters in case if collection is passed as map entry value - all entities will be added under corresponding key. Object.toString will be called otherwise.
     */
    public void addGetParameters(Map<String, Object> getParameters) {
        if (this.getParameters == null) {
            this.getParameters = getParameters;
        } else {
            this.getParameters.putAll(getParameters);
        }
    }

    /**
     * @param value in case if collection passed - all entities will be added under key specified. Object.toString will be called otherwise.
     */
    public void addGetParameter(String key, Object value) {
        if (this.getParameters == null) {
            this.getParameters = new HashMap<String, Object>();
        }
        if (value == null) {
            this.getParameters.remove(key);
        } else {
            this.getParameters.put(key, value);
        }
    }

    public String getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * @param defaultCharset charset, used to encode post body.
     */
    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * @return true if comparison, based on request parameters enabled. (Required for duplicate request filtering)
     */
    public boolean isSmartComparisonEnabled() {
        return smartComparisonEnabled;
    }

    /**
     * @param smartComparisonEnabled true if comparison, based on request parameters enabled. (Required for duplicate request filtering)
     */
    public void setSmartComparisonEnabled(boolean smartComparisonEnabled) {
        this.smartComparisonEnabled = smartComparisonEnabled;
    }

    @Override
    public void cancel() {
        this.syncLock.onResponse(null);
        super.cancel();
    }


    private String getUnparameterizedURL() {
        return super.getUrl();
    }

    @Override
    public String toString() {
        return "BaseRequest{" +
                "requestFormat=" + requestFormat +
                ", responseClasSpecifier=" + responseClasSpecifier +
                ", defaultCharset='" + defaultCharset + '\'' +
                ", responseListener=" + responseListener +
                ", requestHeaders=" + requestHeaders +
                ", postParameters=" + postParameters +
                ", getParameters=" + getParameters +
                ", objectToPost=" + objectToPost +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {

        if (!isSmartComparisonEnabled()) {
            return super.equals(o);
        }

        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseRequest)) {
            return false;
        }

        BaseRequest that = (BaseRequest) o;

        String url = getUnparameterizedURL();
        String otherUrl = that.getUnparameterizedURL();

        if (url != null ? !url.equals(otherUrl) : otherUrl != null) {
            return false;
        }

        if (getParameters != null ? !getParameters.equals(that.getParameters) : that.getParameters != null) {
            return false;
        }
        if (objectToPost != null ? !objectToPost.equals(that.objectToPost) : that.objectToPost != null) {
            return false;
        }
        if (postParameters != null ? !postParameters.equals(that.postParameters) : that.postParameters != null) {
            return false;
        }
        if (requestHeaders != null ? !requestHeaders.equals(that.requestHeaders) : that.requestHeaders != null) {
            return false;
        }
        if (responseClasSpecifier != null ? !responseClasSpecifier.equals(that.responseClasSpecifier) : that.responseClasSpecifier != null) {
            return false;
        }

        if (defaultCharset != null ? !defaultCharset.equals(that.defaultCharset) : that.defaultCharset != null) {
            return false;
        }
        if (requestFormat != that.requestFormat) {
            return false;
        }
        if (responseFormat != that.responseFormat) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {

        if (!isSmartComparisonEnabled()) {
            return super.hashCode();
        }

        int result = requestFormat != null ? requestFormat.hashCode() : 0;
        result = 31 * result + (responseFormat != null ? responseFormat.hashCode() : 0);
        result = 31 * result + (responseClasSpecifier != null ? responseClasSpecifier.hashCode() : 0);
        result = 31 * result + (defaultCharset != null ? defaultCharset.hashCode() : 0);
        result = 31 * result + (requestHeaders != null ? requestHeaders.hashCode() : 0);
        result = 31 * result + (postParameters != null ? postParameters.hashCode() : 0);
        result = 31 * result + (getParameters != null ? getParameters.hashCode() : 0);
        result = 31 * result + (objectToPost != null ? objectToPost.hashCode() : 0);
        result = 31 * result + (getUnparameterizedURL() != null ? getUnparameterizedURL().hashCode() : 0);
        return result;
    }

    private static RequestFuture<ResponseData> getRequestLock() {
        RequestFuture<ResponseData> lock = RequestFuture.newFuture();
        return lock;
    }
}
