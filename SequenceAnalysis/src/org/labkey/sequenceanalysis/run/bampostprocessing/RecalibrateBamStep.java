package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.run.util.BaseRecalibratorWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:46 PM
 */
public class RecalibrateBamStep extends AbstractCommandPipelineStep<BaseRecalibratorWrapper> implements BamProcessingStep
{
    public RecalibrateBamStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new BaseRecalibratorWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<BamProcessingStep>
    {
        public Provider()
        {
            super("RecalibrateBam", "Base Quality Score Recalibration", "GATK", "This will use GATK to perform base quality score recalibration (BQSR) on the BAM file.  This requires your input library to be associated with a set of known SNPs", Arrays.asList(
                ToolParameterDescriptor.createExpDataParam("known_sites_file", "Known Sites", "This should be a VCF file containing sites of known variation.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                    put("extensions", Arrays.asList("vcf", "vcf.gz"));
                    put("width", 400);
                    put("allowBlank", false);
                }}, null)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "https://www.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_gatk_tools_walkers_bqsr_BaseRecalibrator.php");
        }

        @Override
        public BamProcessingStep create(PipelineContext ctx)
        {
            return new RecalibrateBamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".recal.bam");
        output.addIntermediateFile(outputBam);

        File knownSitesFile = null;
        if (!StringUtils.isEmpty(getProvider().getParameterByName("known_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx())))
        {
            knownSitesFile = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("known_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
            if (!knownSitesFile.exists())
            {
                throw new PipelineJobException("Attempting to use a file that does not exist: " + knownSitesFile.getPath());
            }
        }

        getWrapper().execute(referenceGenome.getWorkingFastaFile(), inputBam, outputBam, knownSitesFile);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("BAM not found: " + outputBam.getPath());
        }
        output.setBAM(outputBam);

        return output;
    }
}
