package org.labkey.api.studies;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.studies.study.EventProvider;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.util.List;

/**
 * Created by bimber on 11/3/2016.
 */
abstract public class StudiesService
{
    static StudiesService _instance;

    public static StudiesService get()
    {
        return _instance;
    }

    static public void setInstance(StudiesService instance)
    {
        _instance = instance;
    }

    abstract public void importFolderDefinition(Container container, User user, Module m, Path sourceFolderDirPath) throws IOException;

    abstract public void loadTsv(Resource tsv, String schemaName, User u, Container c);

    abstract public void registerEventProvider(EventProvider ep);

    abstract public List<EventProvider> getEventProviders(Container c);

    abstract public TableCustomizer getStudiesTableCustomizer();
}
