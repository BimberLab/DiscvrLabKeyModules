package org.labkey.jbrowse;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;

/**
 * Created by bimber on 8/25/2014.
 */
public class JBrowseGenomeTrigger implements GenomeTrigger
{
    public JBrowseGenomeTrigger()
    {

    }

    @Override
    public String getName()
    {
        return "JBrowse";
    }

    @Override
    public void onCreate(Container c, User u, Logger log, int genomeId)
    {
        //NOTE: do not auto-create genome resources
        //prepareResourcesForLibrary(c, u, log, genomeId);
    }

    @Override
    public void onRecreate(Container c, User u, Logger log, int genomeId)
    {
        prepareResourcesForLibrary(c, u, log, genomeId);
    }

    @Override
    public void onDelete(Container c, User u, Logger log, int genomeId)
    {
        //TODO
    }

    private void prepareResourcesForLibrary(Container c, User u, Logger log, int genomeId)
    {
        //TODO: consider updating existing sessions that are based on this genome?
    }
}
