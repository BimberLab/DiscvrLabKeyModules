package org.labkey.api.htcondorconnector;

/**
 * Created by bimber on 2/23/2016.
 */
abstract public class HTCondorService
{
    private static HTCondorService _instance;

    public static HTCondorService get()
    {
        return _instance;
    }

    public static void setInstance(HTCondorService instance)
    {
        _instance = instance;
    }

    abstract public void registerResourceAllocator(HTCondorJobResourceAllocator.Factory allocator);
}
