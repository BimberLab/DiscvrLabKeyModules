package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceJobSupportImpl;
import org.labkey.sequenceanalysis.run.alignment.CellRangerWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerRawDataHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);

    public CellRangerRawDataHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Download CellRanger Count Matrices";
    }

    @Override
    public String getDescription()
    {
        return "This will download the raw and filtered gene count matrices for the selected runs.";
    }

    @Override
    public @Nullable String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public @Nullable ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceanalysis/downloadLoupeData.view?outputFileIds=" + (StringUtils.join(outputFileIds, "&outputFileIds=")), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean useWorkbooks()
    {
        return false;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return null;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }
}
