package org.labkey.singlecell.run;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.SingleCellSchema;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class NimbleAlignmentStep extends AbstractCellRangerDependentStep
{
    public static final String REF_GENOMES = "refGenomes";

    public NimbleAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, CellRangerWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Nimble", "This will run Nimble to generate a supplemental feature count matrix for the provided libraries", getCellRangerGexParams(getToolParameters()), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeField.js", "singlecell/panel/NimbleAlignPanel.js")), null, true, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new NimbleAlignmentStep(this, context, new CellRangerWrapper(context.getLogger()));
        }
    }

    public static List<ToolParameterDescriptor> getToolParameters()
    {
        return Arrays.asList(
                ToolParameterDescriptor.create(REF_GENOMES, "Reference Genome(s)", null, "singlecell-nimblealignpanel", null, null),
                ToolParameterDescriptor.create(NimbleHandler.ALIGN_OUTPUT, "Create Alignment/Debug Output", "If checked, an alignment-level summary TSV will be created", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        );
    }

    @Override
    public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        AlignmentOutputImpl output = new AlignmentOutputImpl();
        File localBam = runCellRanger(output, rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);

        // Now run nimble itself:
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());
        helper.doNimbleAlign(localBam, output, rs, basename);
        output.setBAM(localBam);

        return output;
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        super.init(support);

        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());

        List<Integer> genomeIds = helper.getGenomeIds();
        for (int id : genomeIds)
        {
            helper.prepareGenome(id);
        }
    }

    @Override
    public void complete(SequenceAnalysisJobSupport support, AnalysisModel model) throws PipelineJobException
    {
        TableInfo outputFiles = DbSchema.get(SingleCellSchema.SEQUENCE_SCHEMA_NAME, DbSchemaType.Module).getTable(SingleCellSchema.TABLE_OUTPUTFILES);
        List<SequenceOutputFile> outputsCreated = new TableSelector(outputFiles, new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId()), null).getArrayList(SequenceOutputFile.class);
        getPipelineCtx().getLogger().debug("Total sequence outputs created: " + outputsCreated.size());
        for (SequenceOutputFile so : outputsCreated)
        {
            NimbleHelper.importQualityMetrics(so, getPipelineCtx().getJob());
        }
    }
}
