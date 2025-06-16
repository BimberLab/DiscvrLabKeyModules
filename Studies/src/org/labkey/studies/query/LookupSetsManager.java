package org.labkey.studies.query;

import org.apache.logging.log4j.Logger;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.util.logging.LogHelper;

public class LookupSetsManager
{
    private static final LookupSetsManager _instance = new LookupSetsManager();
    private static final Logger _log = LogHelper.getLogger(LookupSetsManager.class, "Messages from the Studies LookupSetsManager");

    public static final String TABLE_LOOKUPS = "lookups";
    public static final String TABLE_LOOKUP_SETS = "lookup_sets";
    private final Cache<String, Object> _cache;

    private LookupSetsManager()
    {
        _cache = CacheManager.getStringKeyCache(1000, CacheManager.UNLIMITED, "LookupSetsManagerCache");
    }

    public static LookupSetsManager get()
    {
        return _instance;
    }

    public Cache<String, Object> getCache()
    {
        return _cache;
    }
}
