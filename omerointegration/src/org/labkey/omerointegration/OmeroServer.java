package org.labkey.omerointegration;

import omero.api.ThumbnailStorePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.LogMessage;
import omero.sys.ParametersI;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.Path;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by bimber on 10/21/2015.
 */
public class OmeroServer
{
    private static final Logger _log = LogManager.getLogger(OmeroServer.class);
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

    private ExperimenterData connect(Gateway gateway, URL url) throws Exception
    {
        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname(url.getHost());
        if (url.getPort() > 0) {
            cred.getServer().setPort(url.getPort());
        }
        cred.getUser().setUsername(getOmeroUser());
        cred.getUser().setPassword(getOmeroPassword());
        ExperimenterData user = gateway.connect(cred);

        return user;
    }

    public void getThumbnail(String omeroId, HttpServletResponse response)
    {
        Long imageId = Long.parseLong(omeroId);

        URL url = getThumbnailUrl(omeroId);
        if (url == null)
            return;

        Gateway gateway = null;
        try
        {
            gateway = new Gateway(new OmeroLogger(_log));
            ExperimenterData user = connect(gateway, url);
            SecurityContext ctx = new SecurityContext(user.getGroupId());

            ParametersI params = new ParametersI();
            params.acquisitionData();
            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image;
            try
            {
                image = browse.getImage(ctx, imageId, params);
            }
            catch (Exception e)
            {
                image = browse.findObject(ctx, ImageData.class, imageId, true);
                Long groupId = image.getGroupId();
                if (groupId > 0 && groupId != ctx.getGroupID())
                {
                    ctx = new SecurityContext(groupId);
                    image = browse.getImage(ctx, imageId, params);
                }
            }

            PixelsData pixels = image.getDefaultPixels();
            ThumbnailStorePrx store = gateway.getThumbnailService(ctx);
            store.setPixelsId(pixels.getId());

            try
            {
                byte[] bytes = store.getThumbnail(omero.rtypes.rint(96), omero.rtypes.rint(96));
                try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes))
                {
                    IOUtils.copy(stream, response.getOutputStream());
                }
            }
            finally
            {
                if (store != null)
                {
                    store.close();
                }
            }
        }
        catch (Exception e)
        {
            //            //TODO: consider streaming error icon??
            //            "/internal/webapp/gxt/images/gray/window/icon-error.gif";
            //            Resource r =

            _log.error("Unable to download thumbnail: " + omeroId, e);
        }
        finally
        {
            if (gateway != null)
            {
                gateway.disconnect();
            }
        }
    }

    private static class OmeroLogger implements omero.log.Logger
    {
        private final Logger _log;

        public OmeroLogger(Logger log)
        {
            _log = log;
        }

        @Override
        public void debug(Object originator, String logMsg)
        {
            _log.debug(logMsg);
        }

        @Override
        public void debug(Object originator, LogMessage msg)
        {
            _log.debug(msg.toString());
        }

        @Override
        public void info(Object originator, String logMsg)
        {
            _log.info(logMsg);
        }

        @Override
        public void info(Object originator, LogMessage msg)
        {
            _log.info(msg.toString());
        }

        @Override
        public void warn(Object originator, String logMsg)
        {
            _log.warn(logMsg);
        }

        @Override
        public void warn(Object originator, LogMessage msg)
        {
            _log.warn(msg.toString());
        }

        @Override
        public void error(Object originator, String logMsg)
        {
            _log.error(logMsg);
        }

        @Override
        public void error(Object originator, LogMessage msg)
        {
            _log.error(msg.toString());
        }

        @Override
        public void fatal(Object originator, String logMsg)
        {
            _log.fatal(logMsg);
        }

        @Override
        public void fatal(Object originator, LogMessage msg)
        {
            _log.fatal(msg.toString());
        }

        @Override
        public String getLogFile()
        {
            return null;
        }
    }
}
