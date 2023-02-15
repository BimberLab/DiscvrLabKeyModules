package org.labkey.sequenceanalysis.run.bampostprocessing;

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
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.IndelRealignerWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:59 PM
 */
public class IndelRealignerStep extends AbstractCommandPipelineStep<IndelRealignerWrapper> implements BamProcessingStep
{
    public IndelRealignerStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new IndelRealignerWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<IndelRealignerStep>
    {
        public Provider()
        {
            super("IndelRealigner", "Indel Realigner", "GATK", "The step runs GATK's IndelRealigner tool.  This tools performs local realignment to minmize the number of mismatching bases across all the reads.", Arrays.asList(
                    ToolParameterDescriptor.create("useQueue", "Use Queue?", "If checked, this tool will attempt to run using GATK queue, allowing parallelization using scatter/gather.", "checkbox", new JSONObject()
                    {{
                        put("checked", false);
                    }}, false),
                    //TODO: consider supporting:
                    //--maxReadsForRealignment
                    //--maxReadsForConsensuses

                    ToolParameterDescriptor.create("minRamPerQueueJob", "Min RAM Per Queue Job", "This only applies if queue is checked.  If provided, the scatter count (number of jobs) for queue will be adjusted to ensure at least this amount of RAM, in GB, is available for each job", "ldk-integerfield", null, null)
            ), null, "http://www.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_sting_gatk_walkers_indels_IndelRealigner.html");
        }

        @Override
        public IndelRealignerStep create(PipelineContext ctx)
        {
            return new IndelRealignerStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);
        getWrapper().setWorkingDir(outputDirectory);

        File dictionary = new File(referenceGenome.getWorkingFastaFile().getParentFile(), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".dict");
        boolean preExistingDictionary = dictionary.exists();
        getPipelineCtx().getLogger().debug("dict exists: " + preExistingDictionary + ", " + dictionary.getPath());

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bam");
        File created;
        if (getProvider().getParameterByName("useQueue").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
        {
            Integer minRamPerQueueJob = getProvider().getParameterByName("minRamPerQueueJob").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            if (minRamPerQueueJob != null)
            {
                getWrapper().setMinRamPerQueueJob(minRamPerQueueJob);
            }

            created = getWrapper().executeWithQueue(inputBam, outputBam, referenceGenome.getWorkingFastaFile(), null);
        }
        else
        {
            created = getWrapper().execute(inputBam, outputBam, referenceGenome.getWorkingFastaFile(), null);
        }

        if (created != null)
        {
            output.setBAM(created);
            output.addIntermediateFile(outputBam);
        }
        else
        {
            // NOTE: this indicates no realignment intervals were found and therefore the skip was skipped.
            // If this occurs, we still need to set a return BAM so the downstream checks pass.
            output.setBAM(inputBam);
        }

        output.addIntermediateFile(getWrapper().getExpectedIntervalsFile(inputBam), "Realigner Intervals File");

        if (!preExistingDictionary)
        {
            if (dictionary.exists())
            {
                output.addIntermediateFile(dictionary);
            }
            else
            {
                getPipelineCtx().getLogger().debug("dict file not found: " + dictionary.getPath());
            }
        }

        //note: we might have sorted the input
        File sortedBam = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".sorted.bam");
        if (sortedBam.exists())
        {
            getPipelineCtx().getLogger().debug("sorted file exists: " + sortedBam.getPath());
            output.addIntermediateFile(sortedBam);
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bai"));
        }
        else
        {
            getPipelineCtx().getLogger().debug("sorted file does not exist: " + sortedBam.getPath());
            output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bai"));
        }

        return output;
    }
}
