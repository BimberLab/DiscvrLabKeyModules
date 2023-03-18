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

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
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
        getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.running, "Running Nimble Align");
        List<NimbleGenome> genomes = getGenomes();
        List<File> jsons = new ArrayList<>();

        for (NimbleGenome genome : genomes)
        {
            File genomeCsv = getGenomeCsv(genome.getGenomeId());
            File genomeFasta = getGenomeFasta(genome.getGenomeId());
            File refJson = prepareReference(genomeCsv, genomeFasta, genome, output);
            output.addIntermediateFile(genomeCsv);
            output.addIntermediateFile(genomeFasta);
            output.addIntermediateFile(refJson);
            jsons.add(refJson);
        }

        Map<NimbleGenome, File> resultMap = doAlignment(genomes, jsons, bam, output);
        for (NimbleGenome genome : genomes)
        {
            File results = resultMap.get(genome);
            if (results == null)
            {
                throw new PipelineJobException("No output recorded for genome : " + genome.getGenomeId());
            }

            if (!results.exists())
            {
                throw new PipelineJobException("Unable to find file: " + results.getPath());
            }

            String description = genome.getScorePercent() > 0 ? "score_percent: " + genome.getScorePercent() : null;
            output.addSequenceOutput(results, basename + ": nimble align", "Nimble Alignment", rs.getRowId(), null, genome.getGenomeId(), description);

            File outputBam = new File(results.getPath().replaceAll("results." + genome.genomeId + ".txt.gz", "nimbleAlignment." + genome.genomeId + ".bam"));
            if (outputBam.exists())
            {
                output.addSequenceOutput(outputBam, basename + ": nimble align", "Nimble Alignment", rs.getRowId(), null, genome.getGenomeId(), description);
            }
            else
            {
                getPipelineCtx().getLogger().debug("BAM not found: " + outputBam.getPath());
            }
        }
    }

    private File prepareReference(File genomeCsv, File genomeFasta, NimbleGenome genome, PipelineStepOutput output) throws PipelineJobException
    {
        genomeCsv = ensureLocalCopy(genomeCsv, output);
        genomeFasta = ensureLocalCopy(genomeFasta, output);

        File nimbleJson = new File(getPipelineCtx().getWorkingDirectory(), genome.genomeId + ".json");
        runUsingDocker(Arrays.asList("python3", "-m", "nimble", "generate", "--opt-file", "/work/" + genomeFasta.getName(), "--file", "/work/" + genomeCsv.getName(), "--output_path", "/work/" + nimbleJson.getName()), output, "generate-" + genome.genomeId);
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
                config.put("score_percent", 0.75);
                config.put("score_filter", 25);
                //discard_multiple_matches: false
                //discard_multi_hits: ?
                //require_valid_pair: false
            }
            else if ("strict".equals(alignTemplate))
            {
                config.put("num_mismatches", 0);
                config.put("intersect_level", 0);
                config.put("score_percent", 0.99);
                config.put("score_threshold", 45);
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

            if (genome.getScorePercent() > 0)
            {
                getPipelineCtx().getLogger().debug("Using custom score_percent: " + genome.getScorePercent());
                config.put("score_percent", genome.getScorePercent());
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

    private Map<NimbleGenome, File> doAlignment(List<NimbleGenome> genomes, List<File> refJsons, File bam, PipelineStepOutput output) throws PipelineJobException
    {
        Map<NimbleGenome, File> resultMap = new HashMap<>();

        File localBam = ensureLocalCopy(bam, output);
        ensureLocalCopy(new File(bam.getPath() + ".bai"), output);

        List<File> localRefJsons = refJsons.stream().map(refJson -> {
            try
            {
                return ensureLocalCopy(refJson, output);
            }
            catch (PipelineJobException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        List<String> alignArgs = new ArrayList<>();
        alignArgs.add("python3");
        alignArgs.add("-m");
        alignArgs.add("nimble");

        alignArgs.add("align");
        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            alignArgs.add("-c");
            alignArgs.add(String.valueOf(maxThreads));
        }

        alignArgs.add("--log");
        alignArgs.add("/work/" + getNimbleLogFile(getPipelineCtx().getWorkingDirectory(), null).getName());

        boolean alignOutput = getProvider().getParameterByName(ALIGN_OUTPUT).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        File alignmentOutputFile = new File(getPipelineCtx().getWorkingDirectory(), "nimbleAlignment.bam");
        if (alignOutput)
        {
            alignArgs.add("--alignment_path");
            alignArgs.add("/work/" + alignmentOutputFile.getName());
        }

        String strandedness = getProvider().getParameterByName(STRANDEDNESS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        if (strandedness != null)
        {
            alignArgs.add("--strand_filter");
            alignArgs.add(strandedness);
        }

        File resultsTsvBase = new File(getPipelineCtx().getWorkingDirectory(), "results.txt");

        alignArgs.add("--reference");
        alignArgs.add(localRefJsons.stream().map(x -> "/work/" + x.getName()).collect(Collectors.joining(",")));

        alignArgs.add("--output");
        alignArgs.add("/work/" + resultsTsvBase.getName());

        alignArgs.add("--input");
        alignArgs.add("/work/" + localBam.getName());

        File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), "align.all");

        boolean dockerRan = runUsingDocker(alignArgs, output, "align.all");
        for (NimbleGenome genome : genomes)
        {
            File resultsTsv = new File(getPipelineCtx().getWorkingDirectory(), "results." + genome.genomeId + ".txt");
            if (dockerRan && !resultsTsv.exists())
            {
                if (doneFile.exists())
                {
                    doneFile.delete();
                }

                throw new PipelineJobException("Expected to find file: " + resultsTsv.getPath());
            }

            File resultsGz = new File(resultsTsv.getPath() + ".gz");
            if (dockerRan)
            {
                if (resultsGz.exists())
                {
                    getPipelineCtx().getLogger().debug("Deleting pre-existing gz output: " + resultsGz.getName());
                    resultsGz.delete();
                }

                // NOTE: perform compression outside of nimble until nimble bugs fixed
                getPipelineCtx().getLogger().debug("Compressing results TSV file");
                resultsGz = Compress.compressGzip(resultsTsv);
                resultsTsv.delete();
            }
            else if (!resultsGz.exists())
            {
                throw new PipelineJobException("Expected to find gz file: " + resultsGz.getPath());
            }

            File log = getNimbleLogFile(resultsGz.getParentFile(), genome.genomeId);
            if (!log.exists())
            {
                throw new PipelineJobException("Expected to find file: " + log.getPath());
            }

            getPipelineCtx().getLogger().info("Nimble alignment stats for genome :" + genome.getGenomeId());
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

            resultMap.put(genome, resultsGz);
        }

        return resultMap;
    }

    public static File getNimbleLogFile(File baseDir, @Nullable Integer genomeId)
    {
        return new File(baseDir, "nimbleStats." + (genomeId == null ? "" : genomeId + ".") + "txt");
    }

    private File getNimbleDoneFile(File parentDir, String resumeString)
    {
        return new File(parentDir, "nimble." + resumeString + ".done");
    }

    public static String DOCKER_CONTAINER_NAME = "ghcr.io/bimberlab/nimble:latest";

    private boolean runUsingDocker(List<String> nimbleArgs, PipelineStepOutput output, String resumeString) throws PipelineJobException
    {
        File localBashScript = new File(getPipelineCtx().getWorkingDirectory(), "docker.sh");
        File dockerBashScript = new File(getPipelineCtx().getWorkingDirectory(), "dockerRun.sh");
        output.addIntermediateFile(localBashScript);
        output.addIntermediateFile(dockerBashScript);

        // Create temp folder:
        File tmpDir = new File(getPipelineCtx().getWorkingDirectory(), "tmpDir");
        if (tmpDir.exists())
        {
            try
            {
                FileUtils.deleteDirectory(tmpDir);
                Files.createDirectory(tmpDir.toPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        output.addIntermediateFile(tmpDir);

        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript);PrintWriter dockerWriter = PrintWriters.getPrintWriter(dockerBashScript))
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
            writer.println("\t-e RUST_BACKTRACE=1 \\");
            writer.println("\t-e TMPDIR=/work/tmpDir \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t--entrypoint /bin/bash \\");
            writer.println("\t-w /work \\");
            writer.println("\t" + DOCKER_CONTAINER_NAME + " \\");
            writer.println("\t/work/" + dockerBashScript.getName());
            writer.println("EXIT_CODE=$?");
            writer.println("echo 'Docker run exit code: '$EXIT_CODE");
            writer.println("exit $EXIT_CODE");

            dockerWriter.println("#!/bin/bash");
            dockerWriter.println("set -x");

            dockerWriter.println(StringUtils.join(nimbleArgs, " "));
            dockerWriter.println("EXIT_CODE=$?");
            dockerWriter.println("echo 'Exit code: '$?");
            dockerWriter.println("ls /work");
            dockerWriter.println("exit $EXIT_CODE");
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
            return false;
        }

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

        return true;
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
        private final double scorePercent;

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

            String rawScore = arr.length() > 3 ? StringUtils.trimToNull(arr.getString(3)) : null;
            scorePercent = rawScore == null ? -1.0 : Double.parseDouble(rawScore);

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

        public double getScorePercent()
        {
            return scorePercent;
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

                        String value = StringUtils.trim(line[1]);
                        if (value == null)
                        {
                            continue;
                        }

                        metricsMap.put(StringUtils.trim(line[0]), value.split(" ")[0]);
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
