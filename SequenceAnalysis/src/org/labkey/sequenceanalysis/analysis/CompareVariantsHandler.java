package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class CompareVariantsHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public CompareVariantsHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Compare Variants";
    }

    @Override
    public String getDescription()
    {
        return "This handler allows you to take multiple input VCF or BED files and compare them to find shared or distinct positions";
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        FileType ft = new FileType(Arrays.asList("vcf"), "vcf", FileType.gzSupportLevel.SUPPORT_GZ);

        return f.getFile() != null && ft.isType(f.getFile());
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList("sequenceanalysis/sequenceAnalysisButtons.js"));
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceAnalysis/variantComparison.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}