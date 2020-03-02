package org.labkey.sequenceanalysis.run.preprocessing;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.log4j.Logger;
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
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TagPcrSummaryStep extends AbstractPipelineStep implements AnalysisStep
{
    public TagPcrSummaryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static final String CACHE_KEY = "tagPcrBlastMap";

    private static final String MIN_ALIGNMENTS = "minAlignments";
    public static class Provider extends AbstractAnalysisStepProvider<TagPcrSummaryStep>
    {
        public Provider()
        {
            super("Tag-PCR", "Tag-PCR Integration Sites", null, "This will produce a table summarizing unique alignments in this BAM.  It was originally created to summarize genomic insertions.", Arrays.asList(
                    ToolParameterDescriptor.create(MIN_ALIGNMENTS, "Min Alignments", "The minimum number of alignments to export a position", "ldk-integerfield", null, 2)
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

        TagPcrWrapper wrapper = new TagPcrWrapper(getPipelineCtx().getLogger());

        String basename = SequenceAnalysisService.get().getUnzippedBaseName(inputBam.getName());
        File siteTable = new File(outputDir, basename + ".sites.txt");
        File primerTable = new File(outputDir, basename + ".primers.txt");
        File genbank = new File(outputDir, basename + ".sites.gb");
        File metrics = getMetricsFile(inputBam, getPipelineCtx().getSourceDirectory());

        wrapper.execute(inputBam, referenceGenome.getWorkingFastaFile(), siteTable, primerTable, genbank, metrics, blastDbs.get(referenceGenome.getGenomeId()));

        output.addOutput(siteTable, "Tag-PCR Integration Sites");
        output.addOutput(primerTable, "Tag-PCR Primer Table");
        output.addOutput(genbank, "Tag-PCR Genbank Summary");
        output.addOutput(metrics, "Tag-PCR Metrics");

        Map<String, String> metricMap = parseMetricFile(metrics);

        NumberFormat pf = NumberFormat.getPercentInstance();
        pf.setMaximumFractionDigits(6);

        output.addSequenceOutput(siteTable,
                "Putative Integration Sites: " + rs.getName(),
                "Tag-PCR Integration Sites", rs.getReadsetId(),
                null,
                referenceGenome.getGenomeId(),
                "Records: " + Integer.parseInt(metricMap.get("NumReadsSpanningJunction")) + "\nJunction hit rate: " +
                        pf.format(Double.parseDouble(metricMap.get("PctReadsSpanningJunction"))
                )
        );

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

    private static class TagPcrWrapper extends AbstractDiscvrSeqWrapper
    {
        public TagPcrWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File bamFile, File referenceFasta, File outputTable, File primerTable, File genbankOutput, File metricsTable, File blastDbBase) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.addAll(getBaseArgs());

            args.add("TagPcrSummary");

            args.add("--bam");
            args.add(bamFile.getPath());

            args.add("-R");
            args.add(referenceFasta.getPath());

            args.add("--output-table");
            args.add(outputTable.getPath());

            args.add("--primer-pair-table");
            args.add(primerTable.getPath());

            args.add("--genbank-output");
            args.add(genbankOutput.getPath());

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

            execute(args);
        }
    }
}
