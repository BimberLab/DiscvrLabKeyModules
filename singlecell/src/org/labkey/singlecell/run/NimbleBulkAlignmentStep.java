package org.labkey.singlecell.run;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class NimbleBulkAlignmentStep extends AbstractAlignmentPipelineStep<NimbleBulkAlignmentStep.NimbleBulkWrapper> implements AlignmentStep
{
    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Nimble-Bulk",
                    "This will run Nimble to generate a supplemental feature count matrix for the provided libraries. This version is intended for bulk input data. Please use the CellRanger/Nimble version for scRNA-seq",
                    NimbleAlignmentStep.getToolParameters(),
                    new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeField.js", "singlecell/panel/NimbleAlignPanel.js")),
                    null,
                    true,  false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public NimbleBulkAlignmentStep create(PipelineContext ctx)
        {
            return new NimbleBulkAlignmentStep(this, ctx, new NimbleBulkWrapper(ctx.getLogger()));
        }
    }

    public NimbleBulkAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx, NimbleBulkAlignmentStep.NimbleBulkWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    @Override
    public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return null;
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());

        List<Integer> genomeIds = helper.getGenomeIds();
        for (int id : genomeIds)
        {
            helper.prepareGenome(id);
        }
    }

    @Override
    public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        AlignmentOutputImpl output = new AlignmentOutputImpl();
        SamtoolsRunner st = new SamtoolsRunner(getPipelineCtx().getLogger());

        List<File> outputBams = new ArrayList<>();
        int bamIdx = 0;
        while (bamIdx < inputFastqs1.size())
        {
            File outputBam = new File(getPipelineCtx().getWorkingDirectory(), FileUtil.makeLegalName(rs.getName()) + ".unmapped." + bamIdx + ".bam");
            List<String> args = new ArrayList<>(Arrays.asList(st.getSamtoolsPath().getPath(), "import", "-o", outputBam.getPath(), "-r", "ID:" + readGroupId));
            if (inputFastqs2 == null || inputFastqs2.isEmpty())
            {
                args.add("-O");
                args.add(inputFastqs1.get(bamIdx).getPath());
            }
            else
            {
                args.add("-1");
                args.add(inputFastqs1.get(bamIdx).getPath());

                if (bamIdx > inputFastqs2.size())
                {
                    throw new PipelineJobException("Unequal lengths for first/second pair FASTQs");
                }

                args.add("-2");
                args.add(inputFastqs2.get(bamIdx).getPath());
            }
            bamIdx++;

            st.execute(args);
            outputBams.add(outputBam);
        }

        File outputBam;
        if (outputBams.size() > 1)
        {
            outputBam = new File(getPipelineCtx().getWorkingDirectory(), FileUtil.makeLegalName(rs.getName()) + ".unmapped.bam");
            outputBams.forEach(output::addIntermediateFile);

            List<String> args = new ArrayList<>(Arrays.asList(st.getSamtoolsPath().getPath(), "merge", "-o", outputBam.getPath(), "-f"));
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("-@");
                args.add(maxThreads.toString());
            }

            outputBams.forEach(bam -> args.add(bam.getPath()));
            st.execute(args);
        }
        else
        {
            outputBam = outputBams.get(0);
        }

        // Now run nimble itself:
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());
        helper.doNimbleAlign(outputBam, output, rs, basename);
        output.setBAM(outputBam);

        return output;
    }

    @Override
    public boolean doAddReadGroups()
    {
        return false;
    }

    @Override
    public boolean doSortIndexBam()
    {
        return false;
    }

    @Override
    public boolean alwaysCopyIndexToWorkingDir()
    {
        return false;
    }

    @Override
    public boolean supportsGzipFastqs()
    {
        return true;
    }

    public static class NimbleBulkWrapper extends AbstractCommandWrapper
    {
        public NimbleBulkWrapper(Logger log)
        {
            super(log);
        }
    }
}
