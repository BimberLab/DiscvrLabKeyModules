package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.run.util.RnaSeQCWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */ 
public class RnaSeQCStep extends AbstractCommandPipelineStep<RnaSeQCWrapper> implements AnalysisStep
{
    public RnaSeQCStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new RnaSeQCWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<RnaSeQCStep>
    {
        public Provider()
        {
            super("RnaSeQC", "RNA-SeQC", "RNA-SeQC", "This runs RNA-SeQC, which generates various QC reports for RNA-Seq data", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "top_transcripts", "Top Transcripts", "Number of top transcripts to use. Default is 1000.", "ldk-integerfield", null, 1000),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-singleEnd"), "singleEnd", "Single Ended Reads?", "This BAM contains single end reads.", "checkbox", null, false),
                    ToolParameterDescriptor.createExpDataParam("gtf", "GTF File", "The GTF file containing genes for this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                            put("extensions", List.of("gtf"));
                            put("width", 400);
                            put("allowBlank", false);
                        }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-ttype"), "ttype", "Transcript Column", "The column in GTF to use to look for rRNA transcript type. Mainly used for running on Ensembl GTF (specify '-ttype 2'). Otherwise, for spec-conforming GTF files, disregard.", "ldk-integerfield", null, 2),
                    ToolParameterDescriptor.createExpDataParam("BWArRNA", "rRNA FASTA", "The dataId of a file representing rRNA sequence.  Use an on the fly BWA alignment for estimating rRNA content. The value should be the rRNA reference fasta. If this flag is absent, rRNA estimation will be based upon the rRNA transcript intervals provided in the GTF (a faster but less robust method).", "ldk-expdatafield", null, null)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js", "ldk/field/ExpDataField.js"), "https://www.broadinstitute.org/cancer/cga/rna-seqc");
        }

        @Override
        public RnaSeQCStep create(PipelineContext ctx)
        {
            return new RnaSeQCStep(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        List<String> extraParams = new ArrayList<>();
        extraParams.addAll(getClientCommandArgs());

        File gtf;
        if (!StringUtils.isEmpty(getProvider().getParameterByName("gtf").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx())))
        {
            gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("gtf").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
            if (!gtf.exists())
            {
                throw new PipelineJobException("Unable to find GTF file: " + gtf.getPath());
            }
        }
        else
        {
            throw new PipelineJobException("No GTF file provided");
        }

        File rnaFasta = null;
        if (!StringUtils.isEmpty(getProvider().getParameterByName("BWArRNA").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx())))
        {
            rnaFasta = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("BWArRNA").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
            if (!rnaFasta.exists())
            {
                throw new PipelineJobException("Unable to find fasta file: " + rnaFasta.getPath());
            }
        }

        getWrapper().execute(Collections.singletonList(inputBam), List.of(rs.getRowId() + ":" + rs.getName()), null, referenceGenome.getWorkingFastaFile(), gtf, outputDir, "rna-seqc", extraParams, rnaFasta);

        File localBwaDir =new File(outputDir, "bwaIndex");
        if (localBwaDir.exists())
        {
            output.addIntermediateFile(localBwaDir);
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
