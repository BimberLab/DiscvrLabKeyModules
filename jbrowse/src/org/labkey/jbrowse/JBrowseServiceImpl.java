package org.labkey.jbrowse;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.DemographicsSource;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.IOException;
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
    private final Logger _log = Logger.getLogger(JBrowseServiceImpl.class);

    private Set<DemographicsSource> _sources = new HashSet<>();

    private JBrowseServiceImpl()
    {

    }

    public static JBrowseServiceImpl get()
    {
        return _instance;
    }

    @Override
    public String prepareOutputFile(User u, Logger log, Integer outputFileId, boolean forceRecreateJson, JSONObject additionalConfig) throws IOException
    {
        JBrowseRoot root = new JBrowseRoot(log);

        JsonFile ret = root.prepareOutputFile(u, outputFileId, forceRecreateJson, additionalConfig);

        return ret == null ? null : ret.getObjectId();
    }

    @Override
    public void reprocessDatabase(Container c, User u, String databaseGuid) throws PipelineValidationException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);

        PipelineService.get().queueJob(JBrowseSessionPipelineJob.recreateDatabase(c, u, root, databaseGuid));
    }

    @Override
    public void registerDemographicsSource(DemographicsSource source)
    {
        _sources.add(source);
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
