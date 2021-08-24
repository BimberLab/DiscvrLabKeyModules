package org.labkey.sequenceanalysis.run.preprocessing;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TagPcrSummaryStep extends AbstractCommandPipelineStep<TagPcrSummaryStep.TagPcrWrapper> implements AnalysisStep
{
    public TagPcrSummaryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new TagPcrWrapper(ctx.getLogger()));
    }

    private static final String CACHE_KEY = "tagPcrBlastMap";

    private static final String OUTPUT_GENBANK = "outputGenbank";
    private static final String DESIGN_PRIMERS = "designPrimers";
    private static final String BACKBONE_SEARCH = "backboneSearch";

    public static class Provider extends AbstractAnalysisStepProvider<TagPcrSummaryStep>
    {
        public Provider()
        {
            super("Tag-PCR", "Map Integration Sites", null, "This will produce a table summarizing unique genome/transgene junctions using a BAM.", Arrays.asList(
                    ToolParameterDescriptor.create(OUTPUT_GENBANK, "Create Genbank Output", "If selected, this will output a genbank file summarizing amplicons and primers", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--insert-name"), "insertType", "Insert Type", "The type of insert to detect.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "PiggyBac;Lentivirus");
                        put("allowBlank", false);
                    }}, "PiggyBac"),
                    ToolParameterDescriptor.create(DESIGN_PRIMERS, "Design Primers", "If selected, Primer3 will be used to design primers to flank integration sites", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--reads-to-output"), "readsToOutput", "Reads To Output Per Site", "If this is non-zero, up to this many reads per integration site will be written to a FASTA file.  This can serve as a way to verify the actual junction border.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mf"), "minFraction", "Min Fraction To Output", "Only sites with at least this fraction of reads will be output.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 5);
                    }}, 0),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-ma"), "minAlignment", "Min Alignments To Output", "Only sites with at least this many alignments will be output.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 3),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--include-sa"), "include-sa", "Include Supplemental Alignments", "If checked, alignments with the SA supplemental alignment tag will be parsed, and these alignments inspected.", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.create(BACKBONE_SEARCH, "Backbone Search Strings", "An optional comma-separated list of search strings to use to mark vector backbone.", "textarea", new JSONObject(){{
                        put("height", 100);
                        put("width", 400);
                    }}, null)
            ), null, null);
        }

        @Override
        public TagPcrSummaryStep create(PipelineContext ctx)
        {
            return new TagPcrSummaryStep(this, ctx);
        }
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        //find/cache BLAST DB
        UserSchema us = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), "blast");
        TableInfo ti = us.getTable("databases", null);

        HashMap<Integer, File> blastDbMap = new HashMap<>();

        for (ReferenceGenome rg : support.getCachedGenomes()) {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("libraryid"), rg.getGenomeId());
            filter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);

            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("objectid", "container"), filter, null);
            if (ts.exists())
            {
                ts.forEachResults(rs -> {
                    Container c = ContainerManager.getForId(rs.getString(FieldKey.fromString("container")));
                    File fileRoot = FileContentService.get().getFileRoot(c, FileContentService.ContentType.files);
                    File ret = new File(fileRoot, ".blastDB");
                    ret = new File(ret.getPath() + "/" + rs.getString(FieldKey.fromString("objectid")));

                    blastDbMap.put(rg.getGenomeId(), ret);
                });
            }
            else
            {
                throw new PipelineJobException("Unable to find BLAST database for: " + rg.getName());
            }

            support.cacheObject(CACHE_KEY, blastDbMap);
        }
    }

    private Map<Integer, File> getCachedBlastDbs(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        return support.getCachedObject(CACHE_KEY, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, Integer.class, File.class));
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        Map<Integer, File> blastDbs = getCachedBlastDbs(getPipelineCtx().getSequenceSupport());

        boolean designPrimers = getProvider().getParameterByName(DESIGN_PRIMERS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        boolean outputGenbank = getProvider().getParameterByName(OUTPUT_GENBANK).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        String backboneSearch = StringUtils.trimToNull(getProvider().getParameterByName(BACKBONE_SEARCH).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null));

        String basename = SequenceAnalysisService.get().getUnzippedBaseName(inputBam.getName());
        File siteTable = new File(outputDir, basename + ".sites.txt");

        File primerTable = null;
        if (designPrimers)
        {
            primerTable = new File(outputDir, basename + ".primers.txt");
        }
        else
        {
            getPipelineCtx().getLogger().info("will not design primers");
        }

        File genbank = null;
        if (outputGenbank)
        {
            genbank = new File(outputDir, basename + ".sites.gb");
        }
        else
        {
            getPipelineCtx().getLogger().info("will not output genbank file");
        }

        File metrics = getMetricsFile(inputBam, outputDir);

        List<String> extraArgs = new ArrayList<>(getClientCommandArgs());

        if (backboneSearch != null) {
            Arrays.stream(backboneSearch.split(",")).forEach(s -> {
                extraArgs.add("-bs");
                extraArgs.add(s);
            });
        }

        getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), siteTable, primerTable, genbank, metrics, blastDbs.get(referenceGenome.getGenomeId()), extraArgs);

        if (siteTable.exists())
        {
            output.addOutput(siteTable, "Tag-PCR Integration Sites");
        }

        if (designPrimers)
        {
            output.addOutput(primerTable, "Tag-PCR Primer Table");
        }

        if (outputGenbank)
        {
            output.addOutput(genbank, "Tag-PCR Genbank Summary");
        }

        output.addOutput(metrics, "Tag-PCR Metrics");

        Map<String, String> metricMap = parseMetricFile(metrics);

        NumberFormat pf = NumberFormat.getPercentInstance();
        pf.setMaximumFractionDigits(4);

        Double hitRate = null;
        if (rs.getReadData() != null)
        {
            getPipelineCtx().getLogger().info("Counting total input reads");

            int totalReads = 0;
            for (ReadData rd : rs.getReadData())
            {
                File forward = rd.getFile1();
                if (forward != null && forward.exists())
                {
                    totalReads += SequenceUtil.getLineCount(forward) / 4;
                }
            }

            getPipelineCtx().getLogger().info("initial reads: " + totalReads);

            if (totalReads > 0)
            {
                hitRate = Integer.parseInt(metricMap.get("NumReadsSpanningJunction")) / (double) totalReads;
                getPipelineCtx().getLogger().info("hit rate: " + hitRate);
            }
        }
        else
        {
            getPipelineCtx().getLogger().error("Readset did not have information on input files");
        }

        if (siteTable.exists())
        {
            output.addSequenceOutput(siteTable,
                    "Putative Integration Sites: " + rs.getName(),
                    "Tag-PCR Integration Sites", rs.getReadsetId(),
                    null,
                    referenceGenome.getGenomeId(),
                    "Reads: " + metricMap.get("NumReadsSpanningJunction") +
                            "\nJunction hit rate (of alignments): " + pf.format(Double.parseDouble(metricMap.get("PctReadsSpanningJunction"))) +
                            (hitRate == null ? "" : "\nJunction hit rate (of total reads): " + pf.format(hitRate)) +
                            "\nIntegration Sites: " + metricMap.get("TotalIntegrationSitesOutput") +
                            "\nMatching Insert Backbone: " + metricMap.get("TotalMatchingInsertBackbone")
            );
        }
        else
        {
            getPipelineCtx().getLogger().info("Site output not found");
        }

        return output;
    }

    private File getMetricsFile(File inputBam, File outDir)
    {
        return new File(outDir, SequenceAnalysisService.get().getUnzippedBaseName(inputBam.getName()) + ".metrics.txt");
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File metrics = getMetricsFile(inputBam, outDir);
        if (metrics.exists())
        {
            getPipelineCtx().getJob().getLogger().info("Loading metrics");
            AtomicInteger total = new AtomicInteger(0);
            TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

            //NOTE: if this job errored and restarted, we may have duplicate records:
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), model.getReadset());
            filter.addCondition(FieldKey.fromString("dataid"), model.getAlignmentFile(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("analysis_id"), model.getRowId(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("category"), "Tag-PCR", CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("container"), getPipelineCtx().getJob().getContainer().getId(), CompareType.EQUAL);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
            if (ts.exists())
            {
                getPipelineCtx().getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                ts.getArrayList(Integer.class).forEach(rowid -> {
                    Table.delete(ti, rowid);
                });
            }

            Map<String, String> metricsMap = parseMetricFile(metrics);
            metricsMap.forEach((metricname, value) -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("category", "Tag-PCR");
                    r.put("metricname", metricname);
                    r.put("metricvalue", value);
                    r.put("analysis_id", model.getRowId());
                    //r.put("dataid", so.getDataId());
                    r.put("readset", model.getReadset());
                    r.put("container", model.getContainer());
                    r.put("createdby", getPipelineCtx().getJob().getUser().getUserId());

                    Table.insert(getPipelineCtx().getJob().getUser(), ti, r);
                    total.getAndIncrement();
                });

                getPipelineCtx().getJob().getLogger().info("total metrics: " + total);

        }
        else
        {
            getPipelineCtx().getJob().getLogger().warn("Unable to find metrics file: " + metrics.getPath());
        }

        return null;
    }

    private Map<String, String> parseMetricFile(File metrics) throws PipelineJobException
    {
        Map<String, String> ret = new HashMap<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(metrics), '\t'))
        {
            String[]line;
            while((line=reader.readNext())!=null)
            {
                if("MetricName".equals(line[0]))
                {
                    continue;
                }

                ret.put(line[0], line[1]);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return ret;
    }

    public static class TagPcrWrapper extends AbstractDiscvrSeqWrapper
    {
        public TagPcrWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File bamFile, File referenceFasta, File outputTable, @Nullable File primerTable, @Nullable File genbankOutput, File metricsTable, File blastDbBase, @Nullable List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.addAll(getBaseArgs());

            args.add("IntegrationSiteMapper");

            args.add("--bam");
            args.add(bamFile.getPath());

            args.add("-R");
            args.add(referenceFasta.getPath());

            args.add("--output-table");
            args.add(outputTable.getPath());

            if (primerTable != null)
            {
                args.add("--primer-pair-table");
                args.add(primerTable.getPath());
            }

            if (genbankOutput != null)
            {
                args.add("--genbank-output");
                args.add(genbankOutput.getPath());
            }

            args.add("--metrics-table");
            args.add(metricsTable.getPath());

            args.add("--primer3-path");
            args.add(SequencePipelineService.get().getExeForPackage("PRIMER3PATH", "primer3_core").getPath());

            args.add("--blastn-path");
            args.add(SequencePipelineService.get().getExeForPackage("BLASTPATH", "blastn").getPath());

            args.add("--blast-db-path");
            args.add(blastDbBase.getPath());

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add("--blast-threads");
                args.add(maxThreads.toString());
            }

            if (extraArgs != null)
            {
                args.addAll(extraArgs);
            }

            execute(args);
        }
    }
}
