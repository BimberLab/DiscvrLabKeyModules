package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//// vulcan -i $FASTQ -clr -t 12 -r $REF -w $WORK_DIR -o $OUT_DIR/${OUT}.bam
public class VulcanWrapper extends AbstractCommandWrapper
{
    public VulcanWrapper(Logger log)
    {
        super(log);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Vulcan", "Vulcan is an extension of minimap2, which adds NGMLR .", List.of(
                    ToolParameterDescriptor.create("readType", "Type", "The type of reads.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("storeValues", "clr;hifi;ont;any");
                        put("multiSelect", false);
                    }}, null)
            ), null, "https://gitlab.com/treangenlab/vulcan", true, false);

            setAlwaysCacheIndex(false);
        }

        @Override
        public String getDescription()
        {
            return null;
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new VulcanAlignmentStep(this, context, new VulcanWrapper(context.getLogger()));
        }
    }

    public static class VulcanAlignmentStep extends AbstractAlignmentPipelineStep<VulcanWrapper> implements AlignmentStep
    {
        public VulcanAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, VulcanWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            return new IndexOutputImpl(referenceGenome);
        }

        @Override
        public final AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);

            if (inputFastqs2 != null)
            {
                throw new PipelineJobException("vulcan expects a single-end FASTQ input");
            }

            AlignmentOutputImpl output = new AlignmentOutputImpl();

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("-i");
            args.add(inputFastq1.getPath());

            args.add("-r");
            args.add(referenceGenome.getWorkingFastaFile().getPath());

            if (!outputDirectory.exists())
            {
                getPipelineCtx().getLogger().debug("Creating output directory: " + outputDirectory.getPath());
                outputDirectory.mkdirs();
            }

            File outBam = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputFastq1.getName()) + ".vulcan");
            args.add("-o");
            args.add(outBam.getPath());

            args.add("-w");
            args.add(outputDirectory.getPath());

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("-t");
                args.add(maxThreads.toString());
            }

            String type = getProvider().getParameterByName("readType").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
            if (type != null)
            {
                args.add("-" + type);
            }

            //args.addAll(getClientCommandArgs());

            getWrapper().execute(args);

            // this will name the BAM based on percentage (defaulting to 90)
            outBam = new File(outBam.getPath() + "_90.bam");
            if (!outBam.exists())
            {
                throw new PipelineJobException("Unable to find BAM: " + outBam.getPath());
            }

            output.addIntermediateFile(new File(outBam.getParentFile(), "minimap2_full_primary.sam"));
            output.addIntermediateFile(new File(outBam.getParentFile(), "minimap2_full.sam"));

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());
            output.setBAM(outBam);

            return output;
        }

        @Override
        public boolean doAddReadGroups()
        {
            return true;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }
    }

    private File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("VULCANPATH", "vulcan");
    }
}
