package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class ClipOverlappingAlignmentsWrapper extends AbstractDiscvrSeqWrapper
{
    public ClipOverlappingAlignmentsWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File inputBam, File fasta, File bed, File outputBam, @Nullable File reportFile) throws PipelineJobException {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("ClipOverlappingAlignments");

        args.add("-R");
        args.add(fasta.getPath());

        args.add("-I");
        args.add(inputBam.getPath());

        args.add("--clipIntervals");
        args.add(bed.getPath());

        args.add("-O");
        args.add(outputBam.getPath());

        if (reportFile != null)
        {
            args.add("--reportFile");
            args.add(reportFile.getPath());
        }

        execute(args);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputBam.getPath());
        }
        return outputBam;
    }

    public static class ClipOverlappingAlignmentsStep extends AbstractCommandPipelineStep<ClipOverlappingAlignmentsWrapper> implements BamProcessingStep
    {
        public ClipOverlappingAlignmentsStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new ClipOverlappingAlignmentsWrapper(ctx.getLogger()));
        }

        public static class Provider extends AbstractPipelineStepProvider<ClipOverlappingAlignmentsWrapper.ClipOverlappingAlignmentsStep>
        {
            public Provider()
            {
                super("ClipOverlappingAlignments", "Clip Overlapping Alignments", "DISCVRseq", "The step runs DISCVRseq's ClipOverlappingAlignments, which will soft-clip and alignments that start/end in the intervals specified in the provided BED file. It was intended for applications such as clipping amplification primers.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("bedFile", "BED File (Intervals)", "This is a BED file specifying the intervals to clip.  Strandedness is ignored.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null)
                ), new LinkedHashSet<>(Arrays.asList("ldk/field/ExpDataField.js")), "https://bimberlab.github.io/DISCVRSeq/");
            }

            @Override
            public ClipOverlappingAlignmentsStep create(PipelineContext ctx)
            {
                return new ClipOverlappingAlignmentsStep(this, ctx);
            }
        }

        @Override
        public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
        {
            BamProcessingOutputImpl output = new BamProcessingOutputImpl();
            getWrapper().setOutputDir(outputDirectory);
            getWrapper().setWorkingDir(outputDirectory);

            File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".clipped.bam");
            File reportFile = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".clipped.txt");

            File bedFile = null;
            Integer bedFileId = getProvider().getParameterByName("bedFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, null);
            if (bedFileId != null)
            {
                bedFile = getPipelineCtx().getSequenceSupport().getCachedData(bedFileId);
                if (!bedFile.exists())
                {
                    throw new PipelineJobException("Unable to find BED file: " + bedFile.getPath());
                }
            }

            getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), bedFile, outputBam, reportFile);

            output.addSequenceOutput(reportFile, rs.getName() + ": alignment clipping report", "Alignment Clipping Report", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
            output.setBAM(outputBam);

            return output;
        }
    }
}
