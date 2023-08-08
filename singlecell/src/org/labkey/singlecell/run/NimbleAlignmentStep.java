package org.labkey.singlecell.run;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class NimbleAlignmentStep extends AbstractCellRangerDependentStep
{
    public static final String REF_GENOMES = "refGenomes";
    public static final String MAX_HITS_TO_REPORT = "maxHitsToReport";
    public static final String ALIGN_OUTPUT = "alignmentOutput";
    public static final String STRANDEDNESS = "strandedness";

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
                ToolParameterDescriptor.create(STRANDEDNESS, "Strandedness Filter", "This will select a pre-defined set of alignment config options", "ldk-simplecombo", new JSONObject(){{
                    put("allowBlank", false);
                    put("storeValues", "unstranded;fiveprime;threeprime");
                    put("initialValues", "unstranded");
                    put("delimiter", ";");
                }}, null),
                ToolParameterDescriptor.create(ALIGN_OUTPUT, "Create Alignment/Debug Output", "If checked, an alignment-level summary TSV will be created", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create(MAX_HITS_TO_REPORT, "Max Hits To Report", "If a given hit has more than this number of references, it is discarded", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 4)
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
}
