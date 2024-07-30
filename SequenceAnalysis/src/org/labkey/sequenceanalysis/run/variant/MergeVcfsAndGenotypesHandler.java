package org.labkey.sequenceanalysis.run.variant;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.MergeVcfsAndGenotypesWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 4/4/2017.
 */
public class MergeVcfsAndGenotypesHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> implements SequenceOutputHandler.HasActionNames
{
    public MergeVcfsAndGenotypesHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Merge Vcfs And Genotypes", "Combine multiple VCF files", null, List.of(
                ToolParameterDescriptor.create("basename", "Output File Name", "This will be used as the name for the output VCF.", "textfield", null, ""),
                ToolParameterDescriptor.create("doSort", "Sort Inputs", "If checked, the input VCFs will be sorted prior to merge. This is usually not necessary", "checkbox", null, false)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.vcf.getFileType().isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public Collection<String> getAllowableActionNames()
    {
        // NOTE: Combine Variants only exists for legacy purposes:
        return PageFlowUtil.set(getName(), "Combine Variants");
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
            File outputVcf = new File(ctx.getOutputDir(), ctx.getParams().getString("basename") + ".combined.vcf.gz");
            boolean doSort = ctx.getParams().optBoolean("doSort", false);

            RecordedAction action = new RecordedAction(getName());

            Set<Integer> genomeIds = new HashSet<>();
            inputFiles.forEach(x -> genomeIds.add(x.getLibrary_id()));
            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The VCFs do not use the same genome");
            }

            List<File> inputVCFs = new ArrayList<>();
            inputFiles.forEach(x -> inputVCFs.add(x.getFile()));
            inputFiles.forEach(x -> action.addInput(x.getFile(), "Input VCF"));

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeIds.iterator().next());

            if (doSort)
            {
                ctx.getLogger().info("Sorting VCFs");
                for (File f : inputVCFs)
                {
                    SequencePipelineService.get().sortVcf(f, null, genome.getSequenceDictionary(), ctx.getLogger());
                }
            }
            else
            {
                ctx.getLogger().debug("Pre-sorting will not be performed");
            }

            new MergeVcfsAndGenotypesWrapper(ctx.getLogger()).execute(genome.getWorkingFastaFile(), inputVCFs, outputVcf, null);
            if (!outputVcf.exists())
            {
                throw new PipelineJobException("unable to find output: " + outputVcf.getPath());
            }

            action.addOutput(outputVcf, "Combined VCF", false);
            SequenceOutputFile so = new SequenceOutputFile();
            so.setName(outputVcf.getName());
            so.setCategory("Combined VCF");
            so.setFile(outputVcf);
            so.setLibrary_id(genomeIds.iterator().next());

            ctx.getFileManager().addSequenceOutput(so);
            ctx.addActions(action);
        }
    }
}
