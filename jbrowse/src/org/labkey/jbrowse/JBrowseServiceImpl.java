package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.jbrowse.DemographicsSource;
import org.labkey.api.jbrowse.GroupsProvider;
import org.labkey.api.jbrowse.JBrowseFieldCustomizer;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.IndexVariantsStep;
import org.labkey.jbrowse.pipeline.JBrowseLucenePipelineJob;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by bimber on 11/3/2016.
 */
public class JBrowseServiceImpl extends JBrowseService
{
    private static final JBrowseServiceImpl _instance = new JBrowseServiceImpl();
    private final Logger _log = LogHelper.getLogger(JBrowseServiceImpl.class, "Messages related to the JBrowse service");

    private final Set<DemographicsSource> _sources = new HashSet<>();
    private final List<GroupsProvider> _providers = new ArrayList<>();
    private final List<JBrowseFieldCustomizer> _customizers = new ArrayList<>();

    private final List<LuceneIndexDetector> _detectors = new ArrayList<>();

    private JBrowseServiceImpl()
    {
        this.registerLuceneIndexDetector(new DefaultLuceneIndexDetector());
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
                ret.prepareResource(u, log, false, true);
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

    @Override
    public void registerFieldCustomizer(JBrowseFieldCustomizer customizer)
    {
        _customizers.add(customizer);
    }

    @Override
    public void prepareLuceneIndex(File vcf, File indexDir, Logger log, List<String> infoFieldsForFullTextSearch, boolean allowLenientLuceneProcessing) throws PipelineJobException
    {
        JBrowseLucenePipelineJob.prepareLuceneIndex(vcf, indexDir, log, infoFieldsForFullTextSearch, allowLenientLuceneProcessing);
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

    public void customizeField(User u, Container c, JBrowseFieldDescriptor field) {
        // NOTE: providers will be registered on module startup, which will be in dependency order.
        // Process them here in this order, so end modules can override earlier ones:
        List<JBrowseFieldCustomizer> customizers = new ArrayList<>(_customizers);
        for (JBrowseFieldCustomizer fc : customizers) {
            if (fc.isAvailable(c, u)) {
                fc.customizeField(field, c, u);
            }
        }
    }

    /***
     * @param groupName
     * @param u
     * @param c
     * @return
     */
    public List<String> resolveGroups(String trackId, String groupName, User u, Container c)
    {
        // NOTE: providers will be registered on module startup, which will be in dependency order.
        // Process them here in reverse dependency order to prioritize end modules
        List<GroupsProvider> providers = new ArrayList<>(_providers);
        Collections.reverse(providers);
        for (GroupsProvider gp : providers)
        {
            if (gp.isAvailable(c, u))
            {
                try
                {
                    List<String> members = gp.getGroupMembers(trackId, groupName, c, u);
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

    public List<String> getGroupNames(User u, Container c)
    {
        Set<String> groups = new TreeSet<>();

        // NOTE: providers will be registered on module startup, which will be in dependency order.
        // Process them here in reverse dependency order to prioritize end modules
        List<GroupsProvider> providers = new ArrayList<>(_providers);
        Collections.reverse(providers);
        for (GroupsProvider gp : providers)
        {
            if (gp.isAvailable(c, u))
            {
                try
                {
                    List<String> gn = gp.getGroupNames(c, u);
                    if (gn != null)
                    {
                        groups.addAll(gn);
                    }
                }
                catch (Exception e)
                {
                    _log.error(e.getMessage(), e);
                }
            }
        }

        return new ArrayList<>(groups);
    }

    public List<String> getPromotedFilters(Collection<String> indexedFields, User u, Container c)
    {
        Set<String> filters = new TreeSet<>();

        // NOTE: providers will be registered on module startup, which will be in dependency order.
        // Process them here in reverse dependency order to prioritize end modules
        List<JBrowseFieldCustomizer> customizers = new ArrayList<>(_customizers);
        Collections.reverse(customizers);
        for (JBrowseFieldCustomizer customizer : customizers)
        {
            if (customizer.isAvailable(c, u))
            {
                try
                {
                    List<String> gn = customizer.getPromotedFilters(indexedFields, c, u);
                    if (gn != null)
                    {
                        filters.addAll(gn);
                    }
                }
                catch (Exception e)
                {
                    _log.error(e.getMessage(), e);
                }
            }
        }

        return new ArrayList<>(filters);
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

    @Override
    public SequenceOutputFile findMatchingLuceneIndex(SequenceOutputFile vcfFile, List<String> infoFieldsToIndex, User u, @Nullable Logger log) throws PipelineJobException
    {
        // NOTE: These are registered in module dependency order, so process in reverse:
        List<LuceneIndexDetector> detectors = new ArrayList<>(_detectors);
        Collections.reverse(detectors);
        for (LuceneIndexDetector li : detectors)
        {
            if (li.isAvailable(vcfFile.getContainerObj()))
            {
                SequenceOutputFile so = li.findMatchingLuceneIndex(vcfFile, infoFieldsToIndex, u, log);
                if (so != null)
                {
                    return so;
                }
            }
        }

        return null;
    }

    @Override
    public void registerLuceneIndexDetector(LuceneIndexDetector detector)
    {
        _detectors.add(detector);
    }

    @Override
    public void cacheDefaultQuery(User u, String sessionId, String trackId)
    {
        JBrowseLuceneSearch luceneSearch = JBrowseLuceneSearch.create(sessionId, trackId, u);
        luceneSearch.cacheDefaultQuery();
    }

    @Override
    public void clearLuceneCacheEntry(File luceneIndexDir)
    {
        JBrowseLuceneSearch.clearCacheForFile(luceneIndexDir);
    }

    public static final class DefaultLuceneIndexDetector implements LuceneIndexDetector
    {
        @Override
        public SequenceOutputFile findMatchingLuceneIndex(SequenceOutputFile vcfFile, List<String> infoFieldsToIndex, User u, @Nullable Logger log) throws PipelineJobException
        {
            if (vcfFile.getContainerObj() == null)
            {
                return null;
            }

            // This forces the index and VCF outputs to live in the same workbook:
            TableInfo ti = QueryService.get().getUserSchema(u, vcfFile.getContainerObj(), JBrowseSchema.SEQUENCE_ANALYSIS).getTable("outputfiles");
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), IndexVariantsStep.CATEGORY);
            AtomicReference<SequenceOutputFile> idxDir = new AtomicReference<>();
            new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null).forEachResults(rs -> {
                SequenceOutputFile so = SequenceOutputFile.getForId(rs.getInt(FieldKey.fromString("rowid")));
                if (so.getFile() == null || !so.getFile().exists())
                {
                    log.error("Sequence output lacks a file: " + so.getRowid());
                    return;
                }

                if (so.getRunId() == null)
                {
                    return;
                }

                ExpRun run = ExperimentService.get().getExpRun(so.getRunId());
                if (run == null)
                {
                    return;
                }

                Map<? extends ExpData, String> inputMap = run.getDataInputs();
                if (inputMap == null)
                {
                    return;
                }

                for (ExpData d : inputMap.keySet())
                {
                    if (!"Input VCF".equals(inputMap.get(d)))
                    {
                        continue;
                    }

                    if (d.getFile() == null || !d.getFile().exists())
                    {
                        continue;
                    }

                    if (vcfFile.getFile().getAbsoluteFile().equals(d.getFile().getAbsoluteFile()))
                    {
                        File fieldsFile = new File(d.getFile().getParentFile(), "fieldList.txt");
                        if (!fieldsFile.exists())
                        {
                            continue;
                        }

                        List<String> fields = new ArrayList<>();
                        try (BufferedReader reader = Readers.getReader(fieldsFile))
                        {
                            String line;
                            while ((line = reader.readLine()) != null)
                            {
                                line = StringUtils.trimToNull(line);
                                if (line != null)
                                {
                                    fields.add(line);
                                }
                            }
                        }
                        catch (IOException e)
                        {
                            if (log != null)
                            {
                                log.error("Unable to read fieldList.txt for: " + d.getFile().getPath(), e);
                                continue;
                            }
                        }

                        if (!infoFieldsToIndex.equals(fields))
                        {
                            if (log != null)
                            {
                                log.info("Partial index match found, but fields to index do not match: " + d.getFile().getPath());
                            }
                            continue;
                        }

                        if (log != null)
                        {
                            log.debug("Identified pre-existing lucene index: " + so.getFile().getPath());
                        }

                        idxDir.set(so);
                        break;
                    }
                }
            });

            return idxDir.get();
        }

        @Override
        public boolean isAvailable(Container c)
        {
            return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(JBrowseModule.class));
        }
    }
}
