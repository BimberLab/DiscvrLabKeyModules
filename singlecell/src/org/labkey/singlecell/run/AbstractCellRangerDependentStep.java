package org.labkey.singlecell.run;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

public class AbstractCellRangerDependentStep extends CellRangerGexCountStep
{
    public AbstractCellRangerDependentStep(AlignmentStepProvider<?> provider, PipelineContext ctx, CellRangerWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    @Override
    public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return super.createIndex(referenceGenome, outputDir);
    }

    protected File runCellRanger(AlignmentOutputImpl output, Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.running, "Running CellRanger GEX");

        File localBam = FileUtil.appendName(outputDirectory, basename + ".cellranger.bam");
        File localBamIdx = SequenceAnalysisService.get().getExpectedBamOrCramIndex(localBam);

        String idParam = StringUtils.trimToNull(getProvider().getParameterByName("id").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        File cellrangerOutdir = FileUtil.appendName(outputDirectory, CellRangerWrapper.getId(idParam, rs));

        if (localBam.exists() && localBamIdx.exists())
        {
            getPipelineCtx().getLogger().info("Existing BAM found, re-using: " + localBam.getPath());
        }
        else
        {
            File crBam = FileUtil.appendName(cellrangerOutdir, "outs/possorted_genome_bam.bam");
            if (crBam.exists())
            {
                getPipelineCtx().getLogger().info("Using previous cellranger count run");
            }
            else
            {
                getPipelineCtx().getLogger().info("Running cellranger");
                AlignmentOutput crOutput = super.performAlignment(rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);
                crBam = crOutput.getBAM();

                // Remove all the normal 10x outputs:
                output.addCommandsExecuted(crOutput.getCommandsExecuted());
                output.addIntermediateFiles(crOutput.getIntermediateFiles());
            }

            // Remove the whole 10x folder:
            output.addIntermediateFile(cellrangerOutdir);

            try
            {
                if (localBam.exists())
                {
                    localBam.delete();
                }
                FileUtils.moveFile(crBam, localBam);

                if (localBamIdx.exists())
                {
                    Files.delete(localBamIdx.toPath());
                }
                FileUtils.moveFile(SequenceAnalysisService.get().getExpectedBamOrCramIndex(crBam), localBamIdx);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return localBam;
    }

    @Override
    public void complete(SequenceAnalysisJobSupport support, AnalysisModel model, Collection<SequenceOutputFile> outputFilesCreated) throws PipelineJobException
    {
        // Skip GEX-specific complete steps.
    }
}
