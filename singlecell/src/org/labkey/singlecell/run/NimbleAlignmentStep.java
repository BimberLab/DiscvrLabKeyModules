package org.labkey.singlecell.run;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class NimbleAlignmentStep extends AbstractCellRangerDependentStep
{
    public static final String REF_GENOMES = "refGenomes";
    public static final String MAX_HITS_TO_REPORT = "maxHitsToReport";
    public static final String STRANDEDNESS = "strandedness";
    public static final String REQUIRE_CACHED_BARCODES = "requireCachedBarcodes";

    public NimbleAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx, CellRangerWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Nimble", "This will run Nimble to generate a supplemental scRNA-seq feature count matrix for the provided libraries", getCellRangerGexParams(getToolParameters()), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeField.js", "singlecell/panel/NimbleAlignPanel.js")), null, true, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
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
                ToolParameterDescriptor.create(MAX_HITS_TO_REPORT, "Max Hits To Report", "If a given hit has more than this number of references, it is discarded", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 4),
                ToolParameterDescriptor.create(REQUIRE_CACHED_BARCODES, "Fail Unless Cached Barcodes Present", "If checked, the pipeline will expect a previously computed map of cellbarcodes and UMIs to be computed. Under default conditions, if this is missing, cellranger will be re-run. This flag can be helpful to avoid that computation if you expect the barcode file to exist.", "checkbox", new JSONObject(){{

                }}, false)
        );
    }

    @Override
    public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        AlignmentOutputImpl output = new AlignmentOutputImpl();

        boolean throwIfNotFound = getProvider().getParameterByName(REQUIRE_CACHED_BARCODES).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        File loupeFile = getCachedLoupeFile(rs, throwIfNotFound);

        File localBam;
        boolean skipTsoTrimming;
        if (loupeFile == null)
        {
            localBam = performCellRangerAlignment(output, rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);
            skipTsoTrimming = false;
        }
        else
        {
            localBam = createNimbleBam(output, rs, inputFastqs1, inputFastqs2);
            skipTsoTrimming = true;
        }


        // Now run nimble itself:
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());
        helper.doNimbleAlign(localBam, output, rs, basename, skipTsoTrimming);
        output.setBAM(localBam);

        return output;
    }

    private File createNimbleBam(AlignmentOutputImpl output, Readset rs, List<File> inputFastqs1, List<File> inputFastqs2) throws PipelineJobException
    {
        File loupeFile = getCachedLoupeFile(rs, true);

        return NimbleHelper.runFastqToBam(output, getPipelineCtx(), rs, inputFastqs1, inputFastqs2, loupeFile);
    }

    private File getCachedLoupeFile(Readset rs, boolean throwIfNotFound) throws PipelineJobException
    {
        LongHashMap<Long> map = getPipelineCtx().getSequenceSupport().getCachedObject(CACHE_KEY, LongHashMap.class);
        Long dataId = map.get(rs.getReadsetId());
        if (dataId == null)
        {
            if (throwIfNotFound)
            {
                throw new PipelineJobException("No cached data found for readset: " + rs.getReadsetId());
            }

            return null;
        }

        File ret = getPipelineCtx().getSequenceSupport().getCachedData(dataId);
        if (ret == null || ! ret.exists())
        {
            throw new PipelineJobException("Missing cached cellbarcode/UMI file: " + dataId);
        }

        return ret;
    }

    private ExpData findLoupeFile(Readset rs) throws PipelineJobException
    {
        Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbookOrTab() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
        UserSchema us = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, SingleCellSchema.SEQUENCE_SCHEMA_NAME);
        TableInfo ti = us.getTable("outputfiles");

        SimpleFilter sf = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
        sf.addCondition(FieldKey.fromString("category"), CellRangerGexCountStep.LOUPE_CATEGORY);
        List<Integer> cbs = new TableSelector(ti, PageFlowUtil.set("dataid"), sf, new Sort("-rowid")).getArrayList(Integer.class);
        if (!cbs.isEmpty())
        {
            int dataId = cbs.get(0);
            ExpData d = ExperimentService.get().getExpData(dataId);
            if (d == null || d.getFile() == null)
            {
                throw new PipelineJobException("Output lacks a file: " + dataId);
            }

            return d;
        }

        return null;
    }

    private File performCellRangerAlignment(AlignmentOutputImpl output, Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        // We need to ensure we keep the BAM for post-processing:
        setAlwaysRetainBam(true);

        File localBam = runCellRanger(output, rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);

        File crDir = new File(localBam.getPath().replace(".nimble.cellranger.bam", ""));
        if (crDir.exists())
        {
            getPipelineCtx().getLogger().debug("Deleting CR output dir: " + crDir.getPath());
            try
            {
                FileUtils.deleteDirectory(crDir);
            }
            catch (IOException e)
            {
                throw new PipelineJobException();
            }
        }

        return localBam;
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

        // Try to find 10x barcodes:
        LongHashMap<Long> readsetToLoupe = new LongHashMap<>();
        for (Readset rs : support.getCachedReadsets())
        {
            ExpData f = findLoupeFile(rs);
            if (f != null)
            {
                support.cacheExpData(f);
                readsetToLoupe.put(rs.getReadsetId(), f.getRowId());
            }
        }

        support.cacheObject(CACHE_KEY, readsetToLoupe);
    }

    private static final String CACHE_KEY = "nimble.loupe";
}
