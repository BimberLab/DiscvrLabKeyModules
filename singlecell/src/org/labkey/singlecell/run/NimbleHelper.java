package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.SingleCellSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.singlecell.run.NimbleAlignmentStep.ALIGN_OUTPUT;
import static org.labkey.singlecell.run.NimbleAlignmentStep.MAX_HITS_TO_REPORT;
import static org.labkey.singlecell.run.NimbleAlignmentStep.REF_GENOMES;
import static org.labkey.singlecell.run.NimbleAlignmentStep.STRANDEDNESS;

public class NimbleHelper
{
    private final PipelineContext _ctx;
    private final PipelineStepProvider<?> _provider;
    private final int _stepIdx;

    public NimbleHelper(PipelineContext ctx, PipelineStepProvider<?> provider, int stepIdx)
    {
        _ctx = ctx;
        _provider = provider;
        _stepIdx = stepIdx;
    }

    private PipelineContext getPipelineCtx()
    {
        return _ctx;
    }

    private PipelineStepProvider<?> getProvider()
    {
        return _provider;
    }

    public int getStepIdx()
    {
        return _stepIdx;
    }

    public List<Integer> getGenomeIds() throws PipelineJobException
    {
        return getGenomes().stream().map(NimbleGenome::getGenomeId).collect(Collectors.toList());
    }

    private List<NimbleGenome> getGenomes() throws PipelineJobException
    {
        String genomeStr = getProvider().getParameterByName(REF_GENOMES).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        if (genomeStr == null)
        {
            throw new PipelineJobException("Missing genomes");
        }

        List<NimbleGenome> ret = new ArrayList<>();
        JSONArray json = new JSONArray(genomeStr);
        int maxHitsToReport = getProvider().getParameterByName(MAX_HITS_TO_REPORT).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 4);
        for (int i = 0; i < json.length(); i++)
        {
            ret.add(new NimbleGenome(json.getString(i), maxHitsToReport));
        }

        return ret;
    }

    public void prepareGenome(int genomeId) throws PipelineJobException
    {
        ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(genomeId, getPipelineCtx().getJob().getUser());
        if (rg == null)
        {
            throw new PipelineJobException("Unable to find genome: " + genomeId);
        }

        getPipelineCtx().getSequenceSupport().cacheGenome(rg);
        getPipelineCtx().getLogger().info("Preparing genome CSV/FASTA for " + rg.getName());
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(getGenomeCsv(genomeId)), ',', CSVWriter.NO_QUOTE_CHARACTER); PrintWriter fastaWriter = PrintWriters.getPrintWriter(getGenomeFasta(genomeId)))
        {
            writer.writeNext(new String[]{"reference_genome", "name", "nt_length", "genbank", "category", "subset", "locus", "lineage", "sequence"});

            Container targetFolder = getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
            TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetFolder, "sequenceanalysis").getTable("reference_library_members");
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("ref_nt_id"), FieldKey.fromString("ref_nt_id/name"), FieldKey.fromString("ref_nt_id/seqLength"), FieldKey.fromString("ref_nt_id/genbank"), FieldKey.fromString("ref_nt_id/category"), FieldKey.fromString("ref_nt_id/subset"), FieldKey.fromString("ref_nt_id/locus"), FieldKey.fromString("ref_nt_id/lineage")));
            TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("library_id"), genomeId), null);
            ts.forEachResults(rs -> {
                List<String> row = new ArrayList<>();
                row.add(rg.getName());
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/name")));
                row.add(String.valueOf(rs.getInt(FieldKey.fromString("ref_nt_id/seqLength"))));
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/genbank")));
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/category")));
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/subset")));
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/locus")));
                row.add(rs.getString(FieldKey.fromString("ref_nt_id/lineage")));

                row = row.stream().map(x -> x == null ? "" : x.replaceAll(",", ";")).collect(Collectors.toList());

                String seq = RefNtSequenceModel.getForRowId(rs.getInt(FieldKey.fromString("ref_nt_id"))).getSequence();
                row.add(seq);
                writer.writeNext(row.toArray(new String[0]));

                fastaWriter.println(">" + rs.getString(FieldKey.fromString("ref_nt_id/name")));
                fastaWriter.println(seq);
            });
        }
        catch(IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getGenomeCsv(int id)
    {
        return new File(getPipelineCtx().getSourceDirectory(), "genome." + id + ".csv");
    }

    private File getGenomeFasta(int id)
    {
        return new File(getPipelineCtx().getSourceDirectory(), "genome." + id + ".fasta");
    }

    public void doNimbleAlign(File bam, PipelineStepOutput output, Readset rs, String basename) throws UnsupportedOperationException, PipelineJobException
    {
        for (NimbleGenome genome : getGenomes())
        {
            getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.running, "Running Nimble for: " + genome.genomeId);

            File genomeCsv = getGenomeCsv(genome.getGenomeId());
            File genomeFasta = getGenomeFasta(genome.getGenomeId());

            File refJson = prepareReference(genomeCsv, genomeFasta, genome, output);
            File results = doAlignment(genome, refJson, bam, output);

            output.addIntermediateFile(genomeCsv);
            output.addIntermediateFile(genomeFasta);
            output.addIntermediateFile(refJson);

            String description = genome.getScoreThreshold() > 0 ? "score_threshold: " + genome.getScoreThreshold() : null;
            output.addSequenceOutput(results, basename + ": nimble align", "Nimble Alignment", rs.getRowId(), null, genome.getGenomeId(), description);
        }
    }

    private File prepareReference(File genomeCsv, File genomeFasta, NimbleGenome genome, PipelineStepOutput output) throws PipelineJobException
    {
        genomeCsv = ensureLocalCopy(genomeCsv, output);
        genomeFasta = ensureLocalCopy(genomeFasta, output);

        File nimbleJson = new File(getPipelineCtx().getWorkingDirectory(), FileUtil.getBaseName(genomeFasta) + ".json");
        runUsingDocker(Arrays.asList("generate", "/work/" + genomeFasta.getName(), "/work/" + genomeCsv.getName(), "/work/" + nimbleJson.getName()), output, "generate-" + genome.genomeId);
        if (!nimbleJson.exists())
        {
            File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), "generate-" + genome.genomeId);
            if (doneFile.exists())
            {
                doneFile.delete();
            }

            throw new PipelineJobException("Unable to find expected file: " + nimbleJson.getPath());
        }

        updateNimbleConfigFile(nimbleJson, genome);

        return nimbleJson;
    }

    private void updateNimbleConfigFile(File configFile, NimbleGenome genome) throws PipelineJobException
    {
        JSONArray json;
        try (BufferedReader reader = Readers.getReader(configFile); StringBuilderWriter writer = new StringBuilderWriter();)
        {
            IOUtils.copy(reader, writer);
            json = new JSONArray(writer.toString());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        JSONObject config = json.getJSONObject(0);
        getPipelineCtx().getLogger().info("Initial config:");
        getPipelineCtx().getLogger().info(config.toString(1));
        try (PrintWriter writer = PrintWriters.getPrintWriter(configFile))
        {
            String alignTemplate = genome.getTemplate();
            if ("lenient".equals(alignTemplate))
            {
                config.put("num_mismatches", 10);
                config.put("intersect_level", 0);
                config.put("score_threshold", 45);
                config.put("score_filter", 25);
                //discard_multiple_matches: false
                //discard_multi_hits: ?
                //require_valid_pair: false
            }
            else if ("strict".equals(alignTemplate))
            {
                config.put("num_mismatches", 0);
                config.put("intersect_level", 0);
                config.put("score_threshold", 50);
                config.put("score_filter", 25);
            }
            else
            {
                throw new PipelineJobException("Unknown value for template: " + genome.getTemplate());
            }

            if (genome.isDoGroup())
            {
                config.put("group_on", "lineage");
            }

            config.put("max_hits_to_report", genome.maxHitsToReport);

            if (genome.getScoreThreshold() > 0)
            {
                getPipelineCtx().getLogger().debug("Using custom score_threshold: " + genome.getScoreThreshold());
                config.put("score_threshold", genome.getScoreThreshold());
            }

            getPipelineCtx().getLogger().info("Final config:");
            getPipelineCtx().getLogger().info(config.toString(1));

            json.put(0, config);
            IOUtils.write(json.toString(), writer);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File doAlignment(NimbleGenome genome, File refJson, File bam, PipelineStepOutput output) throws PipelineJobException
    {
        File localBam = ensureLocalCopy(bam, output);
        ensureLocalCopy(new File(bam.getPath() + ".bai"), output);

        File localRefJson = ensureLocalCopy(refJson, output);
        File resultsTsv = new File(getPipelineCtx().getWorkingDirectory(), "results." + genome.getGenomeId() + ".txt");

        List<String> alignArgs = new ArrayList<>();
        alignArgs.add("align");
        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            alignArgs.add("-c");
            alignArgs.add(String.valueOf(maxThreads));
        }

        alignArgs.add("-l");
        alignArgs.add("/work/nimbleDebug." + genome.genomeId + ".txt");

        boolean alignOutput = getProvider().getParameterByName(ALIGN_OUTPUT).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        if (alignOutput)
        {
            alignArgs.add("-a");
            alignArgs.add("/work/nimbleAlignment." + genome.genomeId + ".txt.gz");
        }

        String strandedness = getProvider().getParameterByName(STRANDEDNESS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        if (strandedness != null)
        {
            alignArgs.add("-f");
            alignArgs.add(strandedness);
        }

        alignArgs.add("/work/" + localRefJson.getName());
        alignArgs.add("/work/" + resultsTsv.getName());
        alignArgs.add("/work/" + localBam.getName());

        runUsingDocker(alignArgs, output, "align-" + genome.genomeId);

        File resultsGz = new File(resultsTsv.getPath() + ".gz");
        if (!resultsTsv.exists() && !resultsGz.exists())
        {
            File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), "align-" + genome.genomeId);
            if (doneFile.exists())
            {
                doneFile.delete();
            }

            throw new PipelineJobException("Expected to find file: " + resultsTsv.getPath());
        }
        else if (!resultsGz.exists())
        {
            // NOTE: perform compression outside of nimble until nimble bugs fixed
            getPipelineCtx().getLogger().debug("Compressing results TSV file");
            resultsGz = Compress.compressGzip(resultsTsv);
            resultsTsv.delete();
        }
        else
        {
            getPipelineCtx().getLogger().debug("Compressed output found, skipping gzip");
        }

        resultsTsv = resultsGz;

        File log = getNimbleLogFile(resultsTsv.getParentFile(), genome.genomeId);
        if (!log.exists())
        {
            throw new PipelineJobException("Expected to find file: " + log.getPath());
        }

        getPipelineCtx().getLogger().info("Nimble alignment stats:");
        try (BufferedReader reader = Readers.getReader(log))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                getPipelineCtx().getLogger().info(line);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return resultsTsv;
    }

    public static File getNimbleLogFile(File baseDir, int genomeId)
    {
        return new File(baseDir, "nimbleDebug." + genomeId + ".txt");
    }

    private File getNimbleDoneFile(File parentDir, String resumeString)
    {
        return new File(parentDir, "nimble." + resumeString + ".done");
    }

    public static String DOCKER_CONTAINER_NAME = "ghcr.io/bimberlab/nimble:latest";

    private void runUsingDocker(List<String> nimbleArgs, PipelineStepOutput output, String resumeString) throws PipelineJobException
    {
        File localBashScript = new File(getPipelineCtx().getWorkingDirectory(), "docker.sh");
        output.addIntermediateFile(localBashScript);

        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull " + DOCKER_CONTAINER_NAME);
            writer.println("sudo $DOCKER run --rm=true \\");

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                //int swap = 4*maxRam;
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-v \"${HOME}:/homeDir\" \\");
            writer.println("\t-u $UID \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t-e TMPDIR=/work \\");
            writer.println("\t-w /work \\");
            writer.println("\t" + DOCKER_CONTAINER_NAME + " \\");
            writer.println("\t" + StringUtils.join(nimbleArgs, " "));
            writer.println("EXIT_CODE=$?");
            writer.println("echo 'Bash script complete: '$EXIT_CODE");
            writer.println("exit $EXIT_CODE");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), resumeString);
        output.addIntermediateFile(doneFile);

        if (doneFile.exists())
        {
            getPipelineCtx().getLogger().info("Nimble already completed, resuming: " + resumeString);
        }
        else
        {
            SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());
            rWrapper.setWorkingDir(getPipelineCtx().getWorkingDirectory());
            rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));

            try
            {
                FileUtils.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private File ensureLocalCopy(File input, PipelineStepOutput output) throws PipelineJobException
    {
        try
        {
            if (getPipelineCtx().getWorkingDirectory().equals(input.getParentFile()))
            {
                return input;
            }

            File local = new File(getPipelineCtx().getWorkingDirectory(), input.getName());
            if (!local.exists())
            {
                getPipelineCtx().getLogger().debug("Copying file locally: " + input.getPath());
                FileUtils.copyFile(input, local);
            }

            output.addIntermediateFile(local);

            return local;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }


    private static class NimbleGenome
    {
        private final int genomeId;
        private final String template;
        private final boolean doGroup;
        private final int maxHitsToReport;
        private final int scoreThreshold;

        public NimbleGenome(String genomeStr, int maxHitsToReport) throws PipelineJobException
        {
            JSONArray arr = new JSONArray(genomeStr);
            if (arr.length() < 3)
            {
                throw new PipelineJobException("Improper genome: " + genomeStr);
            }

            genomeId = arr.getInt(0);
            template = arr.getString(1);
            doGroup = arr.getBoolean(2);
            scoreThreshold = arr.length() > 3 ? arr.getInt(3) : -1;

            this.maxHitsToReport = maxHitsToReport;
        }

        public int getGenomeId()
        {
            return genomeId;
        }

        public String getTemplate()
        {
            return template;
        }

        public boolean isDoGroup()
        {
            return doGroup;
        }

        public int getScoreThreshold()
        {
            return scoreThreshold;
        }
    }

    public static void importQualityMetrics(SequenceOutputFile so, PipelineJob job) throws PipelineJobException
    {
        try
        {
            if (so.getDataId() == null)
            {
                throw new PipelineJobException("DataId is null for SequenceOutputFile");
            }

            ExpData d = ExperimentService.get().getExpData(so.getDataId());
            File cachedMetrics = getNimbleLogFile(so.getFile().getParentFile(), so.getLibrary_id());

            Map<String, Object> metricsMap;
            if (cachedMetrics.exists())
            {
                job.getLogger().debug("reading previously calculated metrics from file: " + cachedMetrics.getPath());
                metricsMap = new HashMap<>();
                try (CSVReader reader = new CSVReader(Readers.getReader(cachedMetrics), ':'))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        if (metricsMap.containsKey(StringUtils.trim(line[0])))
                        {
                            throw new PipelineJobException("Unexpected duplicate metric names: " + StringUtils.trim(line[0]));
                        }

                        metricsMap.put(StringUtils.trim(line[0]), StringUtils.trim(line[1]));
                    }
                }

                job.getLogger().debug("Total metrics: " + metricsMap.size());
            }
            else
            {
                throw new PipelineJobException("Unable to find metrics file: " + cachedMetrics.getPath());
            }

            TableInfo metricsTable = DbSchema.get(SingleCellSchema.SEQUENCE_SCHEMA_NAME, DbSchemaType.Module).getTable(SingleCellSchema.TABLE_QUALITY_METRICS);
            for (String metricName : metricsMap.keySet())
            {
                Map<String, Object> r = new HashMap<>();
                r.put("category", "Nimble");
                r.put("metricname", metricName);
                r.put("metricvalue", metricsMap.get(metricName));
                r.put("dataid", d.getRowId());
                r.put("readset", so.getReadset());
                r.put("container", so.getContainer());
                r.put("createdby", job.getUser().getUserId());

                Table.insert(job.getUser(), metricsTable, r);
            }

            if (cachedMetrics.exists())
            {
                cachedMetrics.delete();
            }
        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }
    }
}
