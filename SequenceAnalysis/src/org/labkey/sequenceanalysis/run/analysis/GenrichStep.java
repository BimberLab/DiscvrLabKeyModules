package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMFileHeader;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenrichStep extends AbstractCommandPipelineStep<GenrichStep.GenrichWrapper> implements AnalysisStep
{
    public GenrichStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new GenrichWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<GenrichStep>
    {
        public Provider()
        {
            super("GenrichStep", "Genrich", null, "This will run Genrich to calculate ATAC-seq peaks.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-r"), "removeDuplicates", "Remove PCR Duplicates", "If checked, PCR duplicates will be removed", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-m"), "minMAPQ", "Min MAPQ", "Minimum MAPQ to keep an alignment", "ldk-integerfield", new JSONObject(){{

                    }}, 0)
            ), null, null);
        }

        @Override
        public GenrichStep create(PipelineContext ctx)
        {
            return new GenrichStep(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File queryNameSortBam;
        try
        {
            if (SequencePipelineService.get().getBamSortOrder(inputBam) != SAMFileHeader.SortOrder.queryname)
            {
                queryNameSortBam = new SamSorter(getPipelineCtx().getLogger()).execute(inputBam, new File(outputDir, FileUtil.getBaseName(inputBam) + ".querySort.bam"), SAMFileHeader.SortOrder.queryname);
                output.addIntermediateFile(queryNameSortBam);
            }
            else
            {
                queryNameSortBam = inputBam;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File outputPeaks = new File(outputDir, FileUtil.getBaseName(inputBam) + ".narrowPeak");

        List<String> extraArgs = getClientCommandArgs();
        getWrapper().run(queryNameSortBam, outputPeaks, extraArgs);

        if (!outputPeaks.exists())
        {
            throw new PipelineJobException("Unable to find file: " + outputPeaks.getPath());
        }

        output.addSequenceOutput(outputPeaks, FileUtil.getBaseName(inputBam) + ": ATAC-seq Peaks", "ATAC-seq Peaks", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    public static class GenrichWrapper extends AbstractCommandWrapper
    {
        public GenrichWrapper(Logger log)
        {
            super(log);
        }

        public void run(File inputBam, File output, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("-t");
            args.add(inputBam.getPath());

            args.add("-o");
            args.add(output.getPath());

            args.add("-j");

            if (extraArgs != null)
            {
                args.addAll(extraArgs);
            }

            execute(args);
        }

        private File getExe()
        {
            return resolveFileInPath("Genrich", "GENRICHPATH", true);
        }
    }
}
