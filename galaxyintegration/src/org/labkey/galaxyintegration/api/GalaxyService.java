package org.labkey.galaxyintegration.api;

import com.github.jmchilton.blend4j.galaxy.GalaxyInstance;
import org.labkey.api.security.User;

import java.util.Collection;

/**
 * Created by bimber on 4/10/2015.
 */
abstract public class GalaxyService
{
    static GalaxyService _instance;

    public static GalaxyService get()
    {
        return _instance;
    }

    public static void setInstance(GalaxyService instance)
    {
        _instance = instance;
    }

    abstract public GalaxyInstance getGalaxyInstance(User u, String hostName);

    /**
     * returns the host names of all servers with API keys registered for the provided user
     */
    abstract public Collection<String> getServerHostNames(User u);
}
