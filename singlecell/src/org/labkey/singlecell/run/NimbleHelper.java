package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;

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
            ret.add(new NimbleGenome(json.getJSONArray(i), maxHitsToReport));
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
        if (AlignerIndexUtil.hasCachedIndex(getPipelineCtx(), "nimble", rg))
        {
            getPipelineCtx().getLogger().debug("Cached index found, will not re-create");
            return;
        }

        getPipelineCtx().getLogger().info("Preparing genome CSV/FASTA for " + rg.getName());
        File csv = getGenomeCsv(genomeId, true);
        File fasta = getGenomeFasta(genomeId, true);
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(csv), ',', CSVWriter.NO_QUOTE_CHARACTER); PrintWriter fastaWriter = PrintWriters.getPrintWriter(fasta))
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
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        AlignerIndexUtil.saveCachedIndex(false, getPipelineCtx(), getLocalIndexDir(genomeId, true), "nimble", rg);
    }

    private File getLocalIndexDir(int genomeId, boolean createIfMissing)
    {
        File dir = new File(getPipelineCtx().getSourceDirectory(), "genome." + genomeId);
        if (createIfMissing && !dir.exists())
        {
            dir.mkdir();
        }

        return dir;
    }

    private File getGenomeCsv(int genomeId) throws PipelineJobException
    {
        return getGenomeCsv(genomeId, false);
    }

    private File getGenomeCsv(int genomeId, boolean forceWorkDir) throws PipelineJobException
    {
        ReferenceGenome rg = getPipelineCtx().getSequenceSupport().getCachedGenome(genomeId);
        if (rg == null)
        {
            throw new PipelineJobException("Unable to find genome: " + genomeId);
        }

        if (!forceWorkDir && AlignerIndexUtil.hasCachedIndex(getPipelineCtx(), "nimble", rg))
        {
            File indexDir = AlignerIndexUtil.getIndexDir(rg, "nimble");
            return new File(indexDir, "genome." + genomeId + ".csv");
        }

        return checkForLegacyGenome(new File(getLocalIndexDir(genomeId, true), "genome." + genomeId + ".csv"));
    }

    private File getGenomeFasta(int genomeId) throws PipelineJobException
    {
        return getGenomeFasta(genomeId, false);
    }

    private File getGenomeFasta(int genomeId, boolean forceWorkDir) throws PipelineJobException
    {
        ReferenceGenome rg = getPipelineCtx().getSequenceSupport().getCachedGenome(genomeId);
        if (rg == null)
        {
            throw new PipelineJobException("Unable to find genome: " + genomeId);
        }

        if (!forceWorkDir && AlignerIndexUtil.hasCachedIndex(getPipelineCtx(), "nimble", rg))
        {
            File indexDir = AlignerIndexUtil.getIndexDir(rg, "nimble");
            return new File(indexDir, "genome." + genomeId + ".fasta");
        }

        return checkForLegacyGenome(new File(getLocalIndexDir(genomeId, true), "genome." + genomeId + ".fasta"));
    }

    // TODO: This should ultimately be removed:
    private File checkForLegacyGenome(File fileNewLocation) throws PipelineJobException
    {
        if (fileNewLocation.exists())
        {
            return fileNewLocation;
        }

        File oldLocation = new File(fileNewLocation.getParentFile().getParentFile(), fileNewLocation.getName());
        if (oldLocation.exists())
        {
            getPipelineCtx().getLogger().debug("Genome file found in old location, moving: " + oldLocation.getPath());
            if (!fileNewLocation.getParentFile().exists())
            {
                fileNewLocation.getParentFile().mkdir();
            }

            try
            {
                FileUtils.moveFile(oldLocation, fileNewLocation);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return fileNewLocation;
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

            // Only add these if they are in the local working directory:
            if (genomeCsv.toPath().startsWith(getPipelineCtx().getWorkingDirectory().toPath()))
            {
                output.addIntermediateFile(genomeCsv);
            }

            if (genomeFasta.toPath().startsWith(getPipelineCtx().getWorkingDirectory().toPath()))
            {
                output.addIntermediateFile(genomeFasta);
            }

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
        try (BufferedReader reader = Readers.getReader(configFile); StringBuilderWriter writer = new StringBuilderWriter())
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
                config.put("num_mismatches", 5);
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

            if (genome.getNumMismatches() > 0)
            {
                getPipelineCtx().getLogger().debug("Using custom num_mismatches: " + genome.getNumMismatches());
                config.put("num_mismatches", genome.getNumMismatches());
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

        boolean alignOutput = getProvider().getParameterByName(ALIGN_OUTPUT).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        File alignmentOutputFile = new File(getPipelineCtx().getWorkingDirectory(), "nimbleAlignment." + (genomes.size() == 1 ? genomes.get(0).genomeId + "." : "") + "bam");
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

        File alignmentTsvBase = new File(getPipelineCtx().getWorkingDirectory(), "alignResults." + (genomes.size() == 1 ? genomes.get(0).genomeId + "." : "") + "txt");

        alignArgs.add("--reference");
        alignArgs.add(localRefJsons.stream().map(x -> "/work/" + x.getName()).collect(Collectors.joining(",")));

        alignArgs.add("--output");
        alignArgs.add("/work/" + alignmentTsvBase.getName());

        alignArgs.add("--input");
        alignArgs.add("/work/" + localBam.getName());

        Integer maxRam = SequencePipelineService.get().getMaxRam();
        if (maxRam != null)
        {
            alignArgs.add("-m");
            alignArgs.add(maxRam + "000"); // in MB
        }

        boolean dockerRan = runUsingDocker(alignArgs, output, "align.all");
        for (NimbleGenome genome : genomes)
        {
            File alignResultsTsv = new File(getPipelineCtx().getWorkingDirectory(), "alignResults." + genome.genomeId + ".txt");
            if (dockerRan && !alignResultsTsv.exists())
            {
                File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), "align.all");
                if (doneFile.exists())
                {
                    doneFile.delete();
                }

                throw new PipelineJobException("Expected to find file: " + alignResultsTsv.getPath());
            }

            File alignResultsGz = new File(alignResultsTsv.getPath() + ".gz");
            if (dockerRan)
            {
                if (alignResultsGz.exists())
                {
                    getPipelineCtx().getLogger().debug("Deleting pre-existing gz output: " + alignResultsGz.getName());
                    alignResultsGz.delete();
                }

                // NOTE: perform compression outside of nimble until nimble bugs fixed
                getPipelineCtx().getLogger().debug("Compressing results TSV file");
                alignResultsGz = Compress.compressGzip(alignResultsTsv);
                alignResultsTsv.delete();
            }
            else if (!alignResultsGz.exists())
            {
                throw new PipelineJobException("Expected to find gz file: " + alignResultsGz.getPath());
            }

            // Now run nimble report. Always re-run since this is fast:
            List<String> reportArgs = new ArrayList<>();
            reportArgs.add("python3");
            reportArgs.add("-m");
            reportArgs.add("nimble");

            reportArgs.add("report");
            reportArgs.add("-i");
            reportArgs.add("/work/" + alignResultsGz.getName());

            File reportResultsGz = new File(getPipelineCtx().getWorkingDirectory(), "reportResults." + genome.genomeId + ".txt");
            if (reportResultsGz.exists())
            {
                reportResultsGz.delete();
            }

            reportArgs.add("-o");
            reportArgs.add("/work/" + reportResultsGz.getName());

            runUsingDocker(reportArgs, output, null);

            if (!reportResultsGz.exists())
            {
                throw new PipelineJobException("Missing file: " + reportResultsGz.getPath());
            }

            resultMap.put(genome, reportResultsGz);
        }

        return resultMap;
    }

    private File getNimbleDoneFile(File parentDir, String resumeString)
    {
        return new File(parentDir, "nimble." + resumeString + ".done");
    }

    public static String DOCKER_CONTAINER_NAME = "ghcr.io/bimberlab/nimble:latest";

    private boolean runUsingDocker(List<String> nimbleArgs, PipelineStepOutput output, @Nullable String resumeString) throws PipelineJobException
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
            dockerWriter.println("exit $EXIT_CODE");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File doneFile = null;
        if (resumeString != null)
        {
            doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), resumeString);
            output.addIntermediateFile(doneFile);

            if (doneFile.exists())
            {
                getPipelineCtx().getLogger().info("Nimble already completed, resuming: " + resumeString);
                return false;
            }
            else
            {
                getPipelineCtx().getLogger().debug("done file not found: " + doneFile.getPath());
            }
        }

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());
        rWrapper.setWorkingDir(getPipelineCtx().getWorkingDirectory());
        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));

        if (doneFile != null)
        {
            try
            {
                FileUtils.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
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
        private final int numMismatches;

        public NimbleGenome(JSONArray arr, int maxHitsToReport) throws PipelineJobException
        {
            if (arr.length() < 3)
            {
                throw new PipelineJobException("Improper genome: " + arr.toString());
            }

            genomeId = arr.getInt(0);
            template = arr.getString(1);
            doGroup = arr.getBoolean(2);

            String rawScore = arr.length() > 3 ? StringUtils.trimToNull(String.valueOf(arr.get(3))) : null;
            scorePercent = rawScore == null ? -1.0 : Double.parseDouble(rawScore);

            String rawMismatches = arr.length() > 4 ? StringUtils.trimToNull(String.valueOf(arr.get(4))) : null;
            numMismatches = rawMismatches == null ? -1 : Integer.parseInt(rawMismatches);

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

        public Integer getNumMismatches()
        {
            return numMismatches;
        }
    }
}
