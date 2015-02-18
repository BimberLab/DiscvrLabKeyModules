package org.labkey.sequenceanalysis;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.*;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.run.util.TabixRunner;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:21 PM
 */
public class SequenceAnalysisServiceImpl extends SequenceAnalysisService
{
    private static SequenceAnalysisServiceImpl _instance = new SequenceAnalysisServiceImpl();

    private final Logger _log = Logger.getLogger(SequenceAnalysisServiceImpl.class);
    private Set<GenomeTrigger> _genomeTriggers = new HashSet<>();
    private Set<SequenceOutputHandler> _fileHandlers = new HashSet<>();
    private Map<String, SequenceDataProvider> _dataProviders = new HashMap<>();

    private SequenceAnalysisServiceImpl()
    {

    }

    public static SequenceAnalysisServiceImpl get()
    {
        return _instance;
    }

    @Override
    public ReferenceLibraryHelper getLibraryHelper(File refFasta)
    {
        return new ReferenceLibraryHelperImpl(refFasta);
    }

    @Override
    public void registerGenomeTrigger(GenomeTrigger trigger)
    {
        _genomeTriggers.add(trigger);
    }

    public Set<GenomeTrigger> getGenomeTriggers()
    {
        return Collections.unmodifiableSet(_genomeTriggers);
    }

    @Override
    public void registerFileHandler(SequenceOutputHandler handler)
    {
        _fileHandlers.add(handler);

        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            LinkedHashSet cds = new LinkedHashSet<>(PageFlowUtil.set(ClientDependency.fromPath("sequenceanalysis/sequenceanalysisButtons.js")));
            if (handler.getClientDependencies() != null)
            {
                cds.addAll(handler.getClientDependencies());
            }
            SimpleButtonConfigFactory fact = new SimpleButtonConfigFactory(handler.getOwningModule(), handler.getName(), "SequenceAnalysis.Buttons.sequenceOutputHandler(dataRegionName, '" + handler.getClass().getCanonicalName() + "')", cds);

            LDKService.get().registerQueryButton(fact, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES);
        }
    }

    @Override
    public File createTabixIndex(File input, @Nullable Logger log) throws PipelineJobException
    {
        return new TabixRunner(log).execute(input);
    }

    public Set<SequenceOutputHandler> getFileHandlers()
    {
        return _fileHandlers;
    }

    @Override
    public void registerDataProvider(SequenceDataProvider p)
    {
        if (_dataProviders.containsKey(p.getKey()))
        {
            _log.error("A SequenceDataProvider with the name: " + p.getName() + " has already been registered");
        }

        _dataProviders.put(p.getName(), p);
    }

    public List<NavItem> getNavItems(Container c, User u, SequenceDataProvider.SequenceNavItemCategory category)
    {
        List<NavItem> ret = new ArrayList<>();
        for (SequenceDataProvider p : _dataProviders.values())
        {
            ret.addAll(p.getSequenceNavItems(c, u, category));
        }

        return ret;
    }
}
