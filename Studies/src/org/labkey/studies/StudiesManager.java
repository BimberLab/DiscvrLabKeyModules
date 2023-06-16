package org.labkey.studies;

public class StudiesManager
{
    private static final StudiesManager _instance = new StudiesManager();

    private StudiesManager()
    {
        // prevent external construction with a private default constructor
    }

    public static StudiesManager get()
    {
        return _instance;
    }
}