package org.labkey.omerointegration;

import omero.api.IContainerPrx;
import omero.api.ServiceFactoryPrx;
import omero.api.ThumbnailStorePrx;
import omero.client;
import omero.model.Image;
import omero.sys.ParametersI;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.Path;
import pojos.ImageData;
import pojos.PixelsData;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 10/21/2015.
 */
public class OmeroServer
{
    private static final Logger _log = Logger.getLogger(OmeroServer.class);
    private Container _container;
    private static final HttpClientConnectionManager _connectionManager = new PoolingHttpClientConnectionManager();

    public OmeroServer(Container c)
    {
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

    private URL getThumbnailUrl(String omeroId)
    {
        String serverUrl = getOmeroUrl();
        if (serverUrl == null)
        {
            throw new IllegalArgumentException("OMERO URL has not been set");
        }

        Path url = Path.parse(serverUrl);
        url = url.append("webclient/render_thumbnail/size/96/" + omeroId);

        try
        {
            URL ret = new URL(url.toString());

            return ret;
        }
        catch (MalformedURLException e)
        {
            throw new IllegalArgumentException(e.getMessage());
        }
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

    private CloseableHttpResponse getResponse(String urlString)
    {
        try
        {
            HttpClientContext httpClientContext = HttpClientContext.create();
            httpClientContext.setCookieStore(new BasicCookieStore());
            HttpRequestBase request = new HttpGet(urlString);
            URL url = new URL(urlString);

            //if a user name was specified, set the credentials
            if (getOmeroPassword() != null)
            {
                AuthScope scope = new AuthScope(url.getHost(), AuthScope.ANY_PORT, AuthScope.ANY_REALM);
                BasicCredentialsProvider provider = new BasicCredentialsProvider();
                Credentials credentials = new UsernamePasswordCredentials(getOmeroUser(), getOmeroPassword());
                provider.setCredentials(scope, credentials);

                httpClientContext.setCredentialsProvider(provider);
                request.addHeader(new BasicScheme().authenticate(credentials, request, httpClientContext));
            }
            else
            {
                httpClientContext.setCredentialsProvider(null);
                request.removeHeaders("Authenticate");
            }

            HttpClientBuilder builder = HttpClientBuilder.create()
                    .setConnectionManager(new PoolingHttpClientConnectionManager())
                    .setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(60000).build())
                    .setDefaultCookieStore(httpClientContext.getCookieStore());
            try
            {
                SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
                sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContextBuilder.build());
                builder.setSSLSocketFactory(sslConnectionSocketFactory);
            }
            catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e)
            {
                throw new RuntimeException(e);
            }

            try (CloseableHttpClient client = builder.build())
            {
                return client.execute(request, httpClientContext);
            }
        }
        catch (AuthenticationException | IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void getThumbnail(String omeroId, HttpServletResponse response) throws Exception
    {
        Long imageId = Long.parseLong(omeroId);

        URL url = getThumbnailUrl(omeroId);
        if (url == null)
            return;

        client client = new client(url.getHost());
        ServiceFactoryPrx entry = client.createSession(getOmeroUser(), getOmeroPassword());

        client unsecureClient = client.createClient(false);
        IContainerPrx proxy = entry.getContainerService();

        List<Image> results = proxy.getImages(Image.class.getName(), Arrays.asList(imageId), new ParametersI());

        if (results.size() == 0)
            return;

        //You can directly interact with the IObject or the Pojos object.
        //Follow interaction with the Pojos.
        ImageData image = new ImageData(results.get(0));

        //See above how to load the image.
        PixelsData pixels = image.getDefaultPixels();
        ThumbnailStorePrx store = entry.createThumbnailStore();
        Map<Long, byte[]> map = store.getThumbnailByLongestSideSet(omero.rtypes.rint(96), Arrays.asList(pixels.getId()));

        //Convert the byte array
        Iterator i = map.entrySet().iterator();

        //Create a buffered image to display
        if (i.hasNext())
        {
            Map.Entry mapEntry = (Map.Entry) i.next();
            try (ByteArrayInputStream stream = new ByteArrayInputStream((byte[]) mapEntry.getValue()))
            {
                IOUtils.copy(stream, response.getOutputStream());
            }
        }
    }
}
