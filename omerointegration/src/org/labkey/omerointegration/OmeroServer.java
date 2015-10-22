package org.labkey.omerointegration;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Created by bimber on 10/21/2015.
 */
public class OmeroServer
{
    private final HttpClient _client;
    private static final Logger _log = Logger.getLogger(OmeroServer.class);
    private Container _container;

    public OmeroServer(Container c)
    {
        _client = new DefaultHttpClient();
        _container = c;
    }

    public boolean isValid() throws IllegalArgumentException
    {
        try
        {
            validateSettings();

            return true;
        }
        catch (IllegalArgumentException e)
        {
            //ignore
        }

        return false;
    }

    public void validateSettings() throws IllegalArgumentException
    {
        if (StringUtils.trimToNull(getOmeroUrl()) == null)
        {
            throw new IllegalArgumentException("OMERO URL has not been set");
        }

        if (StringUtils.trimToNull(getOmeroUser()) == null)
        {
            throw new IllegalArgumentException("OMERO User Id has not been set");
        }

        if (StringUtils.trimToNull(getOmeroPassword()) == null)
        {
            throw new IllegalArgumentException("OMERO Password has not been set");
        }
    }

    private String getThumbnailUrl(String omeroId)
    {
        String serverUrl = getOmeroUrl();
        if (serverUrl == null)
        {
            throw new IllegalArgumentException("OMERO URL has not been set");
        }

        Path url = Path.parse(serverUrl);
        url = url.append("webclient/render_thumbnail/size/96/" + omeroId);

        return url.toString();
    }

    public String getViewerUrl(String omeroId)
    {
        String serverUrl = getOmeroUrl();
        if (serverUrl == null)
        {
            throw new IllegalArgumentException("OMERO URL has not been set");
        }

        Path url = Path.parse(serverUrl);
        url = url.append("webclient/img_detail/" + omeroId);

        return url.toString();
    }

    private String getOmeroUrl()
    {
        return getProperty(OmeroIntegrationManager.OMERO_URL);
    }

    private String getOmeroUser()
    {
        return StringUtils.trimToNull(getProperty(OmeroIntegrationManager.OMERO_USERNAME));
    }

    private String getOmeroPassword()
    {
        return StringUtils.trimToNull(getProperty(OmeroIntegrationManager.OMERO_PASSWORD));
    }

    private String getProperty(String name)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getProperties(_container, OmeroIntegrationManager.CONFIG_PROPERTY_DOMAIN);

        return map == null ? null : map.get(name);
    }

    private HttpContext getContext(HttpRequestBase request)
    {
        if (getOmeroPassword() != null)
        {
            _log.info("using basic auth for central function request");

            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(request.getURI().getHost(), request.getURI().getPort()),
                    new UsernamePasswordCredentials(getOmeroUser(), getOmeroPassword()));

            HttpClientContext localContext = HttpClientContext.create();
            localContext.setCredentialsProvider(credsProvider);

            return localContext;
        }

        return null;
    }

    private HttpEntity getHttpEntity(HttpRequestBase request) throws IOException, ClientProtocolException
    {
        HttpContext ctx = getContext(request);

        //TODO: remove after debugging
        _log.info("omero request: " + request.getURI().toString());

        HttpResponse response = ctx == null ? _client.execute(request) : _client.execute(request, ctx);
        return response.getEntity();
    }

    public HttpEntity getThumbnail(String omeroId) throws IOException, ClientProtocolException
    {
        String url = getThumbnailUrl(omeroId);
        if (url == null)
            return null;

        return getHttpEntity(new HttpGet(url));
    }
}
