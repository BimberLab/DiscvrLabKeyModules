package org.labkey.jbrowse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.DemographicsSource;
import org.labkey.api.jbrowse.GroupsProvider;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 11/3/2016.
 */
public class JBrowseServiceImpl extends JBrowseService
{
    private static final JBrowseServiceImpl _instance = new JBrowseServiceImpl();
    private final Logger _log = LogHelper.getLogger(JBrowseServiceImpl.class, "Messages related to the JBrowse service");

    private final Set<DemographicsSource> _sources = new HashSet<>();
    private final List<GroupsProvider> _providers = new ArrayList<>();

    private JBrowseServiceImpl()
    {

    }

    public static JBrowseServiceImpl get()
    {
        return _instance;
    }

    @Override
    public String prepareOutputFile(User u, Logger log, Integer outputFileId, boolean forceRecreateJson, JSONObject additionalConfig)
    {
        JsonFile ret = JsonFile.prepareJsonFileRecordForOutputFile(u, outputFileId, additionalConfig, log);
        if (ret != null && ret.needsProcessing())
        {
            try
            {
                ret.prepareResource(log, false, true);
            }
            catch (PipelineJobException e)
            {
                log.error("Unable to prepare JBrowse resource: " + outputFileId, e);
            }
        }

        return ret == null ? null : ret.getObjectId();
    }

    @Override
    public void onGenomeChange(Container c, User u, int genomeId, Logger log) throws PipelineJobException
    {
        JBrowseManager.get().ensureGenomePrepared(c, u, genomeId, log);
    }

    @Override
    public void reprocessDatabase(User u, String databaseGuid) throws PipelineValidationException
    {
        JBrowseSession session = JBrowseSession.getForId(databaseGuid);
        if (session == null)
        {
            throw new IllegalArgumentException("Unable to find session: " + databaseGuid);
        }

        PipeRoot root = PipelineService.get().getPipelineRootSetting(session.getContainerObj());
        PipelineService.get().queueJob(JBrowseSessionPipelineJob.refreshDatabase(session.getContainerObj(), u, root, databaseGuid));
    }

    @Override
    public void registerDemographicsSource(DemographicsSource source)
    {
        _sources.add(source);
    }

    @Override
    public void registerGroupsProvider(GroupsProvider provider)
    {
        _providers.add(provider);
    }

    public Map<String, Map<String, Object>> resolveSubjects(List<String> subjects, User u, Container c)
    {
        Map<String, Map<String, Object>> ret = new HashMap<>();
        for (DemographicsSource s : _sources)
        {
            if (s.isAvailable(c, u))
            {
                try
                {
                    Map<String, Map<String, Object>> r = s.resolveSubjects(subjects, c, u);
                    if (r != null)
                    {
                        for (String subject : r.keySet())
                        {
                            Map<String, Object> subjectMap = ret.getOrDefault(subject, new CaseInsensitiveHashMap<>());
                            subjectMap.putAll(r.get(subject));
                            ret.put(subject, subjectMap);
                        }
                    }
                }
                catch (Exception e)
                {
                    _log.error(e.getMessage(), e);
                }
            }
        }

        return ret;
    }

    /***
     * @param groupName
     * @param u
     * @param c
     * @return
     */
    public Set<String> resolveGroups(String groupName, User u, Container c)
    {
        // NOTE: providers will be registered on module startup, which will be in dependency order.
        // Process them here in reverse dependency order so we prioritize end modules
        List<GroupsProvider> providers = new ArrayList<>(_providers);
        Collections.reverse(providers);
        for (GroupsProvider gp : providers)
        {
            if (gp.isAvailable(c, u))
            {
                try
                {
                    Set<String> members = gp.getGroupMembers(groupName, c, u);
                    if (members != null)
                    {
                        return members;
                    }
                }
                catch (Exception e)
                {
                    _log.error(e.getMessage(), e);
                }
            }
        }

        return null;
    }

    public Map<String, String> getDemographicsFields(User u, Container c)
    {
        Map<String, String> ret = new LinkedHashMap<>();
        for (DemographicsSource s : _sources)
        {
            if (s.isAvailable(c, u))
            {
                ret.putAll(s.getFields());
            }
        }

        return ret;
    }
}
