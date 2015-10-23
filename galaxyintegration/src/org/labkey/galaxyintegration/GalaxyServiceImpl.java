package org.labkey.galaxyintegration;

import com.github.jmchilton.blend4j.galaxy.GalaxyInstance;
import com.github.jmchilton.blend4j.galaxy.GalaxyInstanceFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.galaxyintegration.api.GalaxyService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 4/10/2015.
 */
public class GalaxyServiceImpl extends GalaxyService
{
    private static GalaxyServiceImpl _instance = new GalaxyServiceImpl();

    private final Logger _log = Logger.getLogger(GalaxyServiceImpl.class);

    private GalaxyServiceImpl()
    {

    }

    public static GalaxyServiceImpl get()
    {
        return _instance;
    }

//    @Override
//    public GalaxyInstance getGalaxyInstance(User u, String hostName)
//    {
//        Map<String, String> map = PropertyManager.getProperties(u, ContainerManager.getRoot(), GalaxyIntegrationManager.GALAXY_KEY);
//        String apiKey = map == null ? null : map.get(hostName);
//
//        if (apiKey == null)
//        {
//            return null;
//        }
//
//        return GalaxyInstanceFactory.get(hostName, apiKey);
//    }

    @Override
    @NotNull
    public Collection<String> getServerHostNames(User u)
    {
        Set<String> ret = new HashSet<>();

        Map<String, String> map = PropertyManager.getProperties(u, ContainerManager.getRoot(), GalaxyIntegrationManager.GALAXY_KEY);
        if (map != null)
        {
            ret.addAll(map.keySet());
        }

        return Collections.unmodifiableCollection(ret);
    }
}
