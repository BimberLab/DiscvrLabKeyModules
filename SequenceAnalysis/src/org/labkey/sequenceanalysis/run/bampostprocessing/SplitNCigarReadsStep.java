package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.SplitNCigarReadsWrapper;

import java.io.File;
import java.util.Collections;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:46 PM
 */
public class SplitNCigarReadsStep extends AbstractCommandPipelineStep<SplitNCigarReadsWrapper> implements BamProcessingStep
{
    public SplitNCigarReadsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SplitNCigarReadsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<BamProcessingStep>
    {
        public Provider()
        {
            super("SplitNCigarReads", "Split N Cigar Reads", "GATK", "This will use GATK to Splits reads that contain Ns in their CIGAR string.  It is most commonly used for RNA-Seq", Collections.singletonList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-skip-mq-transform"), "skip-mq-transform", "Skip Mapping Quality Transform", "This flag turns off the mapping quality 255 -> 60 read transformer. The transformer is on by default to ensure that uniquely mapping reads assigned STAR's default 255 MQ aren't filtered out by HaplotypeCaller.", "checkbox", null, false)
            ), null, "https://www.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_gatk_tools_walkers_rnaseq_SplitNCigarReads.php");
        }

        @Override
        public BamProcessingStep create(PipelineContext ctx)
        {
            return new SplitNCigarReadsStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".splitncigar.bam");
        output.addIntermediateFile(outputBam);

        getWrapper().execute(referenceGenome.getWorkingFastaFile(), inputBam, outputBam, getClientCommandArgs());

        if (!outputBam.exists())
        {
            throw new PipelineJobException("BAM not found: " + outputBam.getPath());
        }
        output.setBAM(outputBam);

        return output;
    }
}
