package org.labkey.sequenceanalysis.run.preprocessing;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.run.alignment.BWAMemWrapper;
import org.labkey.sequenceanalysis.run.analysis.UnmappedReadExportHandler;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilterReadsStep extends AbstractPipelineStep implements PreprocessingStep
{
    private static final String GENOME = "genomeId";
    private static final String MIN_QUAL = "minQual";

    public FilterReadsStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("FilterMatchingReads", "Filter Reads Matching Reference", "BWA-mem/samtools", "This step aligns input reads against a reference using BWA-mem and will only return read pairs without a passing hit in either read.", Arrays.asList(
                    new GenomeParam(),
                    ToolParameterDescriptor.create(MIN_QUAL, "Min MAPQ", "Only alignments with MAPQ greater than this value will be considered", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50)
            ), null, null);
        }

        @Override
        public FilterReadsStep create(PipelineContext context)
        {
            return new FilterReadsStep(this, context);
        }
    }

    @Override
    public Output processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

        BWAMemWrapper wrapper = new BWAMemWrapper(getPipelineCtx().getLogger());
        List<String> bwaArgs = new ArrayList<>();

        int minMapq = getProvider().getParameterByName(MIN_QUAL).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
        if (minMapq > 0)
        {
            bwaArgs.add("-T");
            bwaArgs.add(String.valueOf(minMapq));
        }

        int genomeId = getProvider().getParameterByName(GENOME).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
        ReferenceGenome genome = getPipelineCtx().getSequenceSupport().getCachedObject(GenomeParam.getCacheKey(genomeId), ReferenceGenome.class);

        AlignmentOutputImpl alignmentOutput = new AlignmentOutputImpl();
        String basename = SequenceAnalysisService.get().getUnzippedBaseName(inputFile.getName()) + ".filterAlign";
        wrapper.performMemAlignment(getPipelineCtx().getJob(), alignmentOutput, inputFile, inputFile2, outputDir, genome, basename, bwaArgs);

        SequencePipelineService.get().ensureBamIndex(alignmentOutput.getBAM(), getPipelineCtx().getLogger(), true);
        
        output.addIntermediateFile(alignmentOutput.getBAM());
        output.addIntermediateFile(SequenceUtil.getExpectedIndex(alignmentOutput.getBAM()));

        File unmappedReadsF = new File(alignmentOutput.getBAM().getParentFile(), FileUtil.getBaseName(alignmentOutput.getBAM()) + "_unmapped_F.fastq");
        File unmappedReadsR = new File(alignmentOutput.getBAM().getParentFile(), FileUtil.getBaseName(alignmentOutput.getBAM()) + "_unmapped_R.fastq");
        File singletons = new File(alignmentOutput.getBAM().getParentFile(), FileUtil.getBaseName(alignmentOutput.getBAM()) + "_unmapped_singletons.fastq");

        UnmappedReadExportHandler.Processor.writeUnmappedReadsAsFastq(alignmentOutput.getBAM(), unmappedReadsF, unmappedReadsR, singletons, getPipelineCtx().getLogger());
        output.addIntermediateFile(singletons);

        output.setProcessedFastq(Pair.of(unmappedReadsF, unmappedReadsR));

        return output;
    }

    public static class GenomeParam extends ToolParameterDescriptor implements ToolParameterDescriptor.CachableParam
    {
        public GenomeParam()
        {
            super(null, GENOME, "Choose Genome", "Select a previously saved reference genome from the list.", "ldk-simplelabkeycombo", null, new JSONObject()
            {{
                put("width", 450);
                put("schemaName", "sequenceanalysis");
                put("queryName", "reference_libraries");
                put("containerPath", "js:Laboratory.Utils.getQueryContainerPath()");
                put("filterArray", "js:[LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)]");
                put("displayField", "name");
                put("valueField", "rowid");
                put("allowBlank", false);
            }});
        }

        @Override
        public void doCache(PipelineJob job, Object value, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            if (value !=  null)
            {
                int genomeId = ConvertHelper.convert(value, Integer.class);
                support.cacheObject(getCacheKey(genomeId), SequenceAnalysisService.get().getReferenceGenome(genomeId, job.getUser()));
            }
        }

        public static String getCacheKey(int genomeId)
        {
            return "filterReadStep." + genomeId;
        }
    }
}
