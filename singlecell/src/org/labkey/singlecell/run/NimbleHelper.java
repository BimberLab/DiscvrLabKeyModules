package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.sequenceanalysis.run.DockerWrapper;
import org.labkey.api.util.FileUtil;
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

import static org.labkey.singlecell.run.NimbleAlignmentStep.MAX_HITS_TO_REPORT;
import static org.labkey.singlecell.run.NimbleAlignmentStep.REF_GENOMES;
import static org.labkey.singlecell.run.NimbleAlignmentStep.STRANDEDNESS;

public class NimbleHelper
{
    private final PipelineContext _ctx;
    private final PipelineStepProvider<?> _provider;
    private final int _stepIdx;

    public static final String NIMBLE_REPORT_CATEGORY = "Nimble Report";

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

        String nimbleVersion = getVersion(output);

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

            String description = "Nimble version: " + nimbleVersion;
            if (genome.getScorePercent() > 0)
            {
                description += "\nscore_percent: " + genome.getScorePercent();
            }

            output.addSequenceOutput(results, basename + ": nimble align", "Nimble Results", rs.getRowId(), null, genome.getGenomeId(), description);

            // NOTE: situations like zero alignments would result in no report being created. Rely on the code in doAlign to verify proper execution of nimble
            File reportHtml = getReportHtmlFileFromResults(results);
            if (reportHtml.exists())
            {
                output.addSequenceOutput(reportHtml, basename + ": nimble report", NIMBLE_REPORT_CATEGORY, rs.getRowId(), null, genome.getGenomeId(), description);
            }
        }
    }

    private File prepareReference(File genomeCsv, File genomeFasta, NimbleGenome genome, PipelineStepOutput output) throws PipelineJobException
    {
        File nimbleJson = new File(getPipelineCtx().getWorkingDirectory(), genome.genomeId + ".json");
        runUsingDocker(Arrays.asList("python3", "-m", "nimble", "generate", "--opt-file", genomeFasta.getPath(), "--file", genomeCsv.getPath(), "--output_path", nimbleJson.getPath()), output, "generate-" + genome.genomeId);
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
            throw new PipelineJobException("Unable to parse JSON: " + configFile.getPath(), e);
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
                // NOTE: score_percent should almost always supersede this value
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
                // NOTE: this allows a small amount of mismatched ends:
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

        String strandedness = getProvider().getParameterByName(STRANDEDNESS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        if (strandedness != null)
        {
            alignArgs.add("--strand_filter");
            alignArgs.add(strandedness);
        }

        File alignmentTsvBase = new File(getPipelineCtx().getWorkingDirectory(), "alignResults." + (genomes.size() == 1 ? genomes.get(0).genomeId + "." : "") + "txt.gz");

        alignArgs.add("--reference");
        alignArgs.add(refJsons.stream().map(File::getPath).collect(Collectors.joining(",")));

        alignArgs.add("--output");
        alignArgs.add(alignmentTsvBase.getPath());

        alignArgs.add("--input");
        alignArgs.add(bam.getPath());

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

        alignArgs.add("--tmpdir");
        alignArgs.add(tmpDir.getPath());

        boolean dockerRan = runUsingDocker(alignArgs, output, "align.all");

        // Because this can be large, delete it quickly:
        if (tmpDir.exists())
        {
            try
            {
                getPipelineCtx().getLogger().debug("Deleting nimble temp dir");
                FileUtils.deleteDirectory(tmpDir);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        for (NimbleGenome genome : genomes)
        {
            File alignResultsGz = new File(getPipelineCtx().getWorkingDirectory(), "alignResults." + genome.genomeId + ".txt.gz");
            if (dockerRan && !alignResultsGz.exists())
            {
                File doneFile = getNimbleDoneFile(getPipelineCtx().getWorkingDirectory(), "align.all");
                if (doneFile.exists())
                {
                    doneFile.delete();
                }

                throw new PipelineJobException("Expected to find file: " + alignResultsGz.getPath());
            }

            if (!alignResultsGz.exists())
            {
                throw new PipelineJobException("Expected to find gz file: " + alignResultsGz.getPath());
            }

            // Now run nimble report. Always re-run since this is fast:
            File reportResultsGz =  runNimbleReport(alignResultsGz, genome.genomeId, output, getPipelineCtx());
            resultMap.put(genome, reportResultsGz);
        }

        return resultMap;
    }

    public static final String CATEGORY_CB_UMI = "10x CellBarcode/UMI Map";

    public static void write10xBarcodes(File bam, Logger log, Readset rs, ReferenceGenome referenceGenome, PipelineStepOutput output) throws PipelineJobException
    {
        log.info("Writing 10x CB/UMIs to TSV");
        DISCVRSeqRunner runner = new DISCVRSeqRunner(log);
        List<String> barcodeArgs = new ArrayList<>(runner.getBaseArgs("Save10xBarcodes"));
        barcodeArgs.add("-I");
        barcodeArgs.add(bam.getPath());

        File bcOutput = new File(bam.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(bam.getName()) + ".bc.txt.gz");
        barcodeArgs.add("--output");
        barcodeArgs.add(bcOutput.getPath());

        runner.execute(barcodeArgs);

        output.addSequenceOutput(bcOutput, "10x CellBarcode/UMI Map: " + rs.getName(), CATEGORY_CB_UMI, rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
    }

    public static File runNimbleReport(File alignResultsGz, int genomeId, PipelineStepOutput output, PipelineContext ctx) throws PipelineJobException
    {
        List<String> reportArgs = new ArrayList<>();
        reportArgs.add("python3");
        reportArgs.add("-m");
        reportArgs.add("nimble");

        reportArgs.add("report");
        reportArgs.add("-i");
        reportArgs.add(alignResultsGz.getPath());

        String resumeString = "nimble.report." + genomeId;
        File doneFile = getNimbleDoneFile(ctx.getWorkingDirectory(), resumeString);

        File reportResultsGz = new File(ctx.getWorkingDirectory(), "reportResults." + genomeId + ".txt.gz");
        if (reportResultsGz.exists() && !doneFile.exists())
        {
            ctx.getLogger().debug("Deleting existing result file: " + reportResultsGz.getPath());
            reportResultsGz.delete();
        }

        reportArgs.add("-o");
        reportArgs.add(reportResultsGz.getPath());

        runUsingDocker(reportArgs, output, resumeString, ctx);

        if (!reportResultsGz.exists())
        {
            throw new PipelineJobException("Missing file: " + reportResultsGz.getPath());
        }

        if (SequencePipelineService.get().hasMinLineCount(alignResultsGz, 2))
        {
            // Also run nimble plot. Always re-run since this is fast:
            List<String> plotArgs = new ArrayList<>();
            plotArgs.add("python3");
            plotArgs.add("-m");
            plotArgs.add("nimble");

            plotArgs.add("plot");
            plotArgs.add("--input_file");
            plotArgs.add(alignResultsGz.getPath());

            File plotResultsHtml = getReportHtmlFileFromResults(reportResultsGz);
            if (plotResultsHtml.exists())
            {
                plotResultsHtml.delete();
            }

            plotArgs.add("--output_file");
            plotArgs.add(plotResultsHtml.getPath());

            runUsingDocker(plotArgs, output, "nimble.plot." + genomeId, ctx);

            if (!plotResultsHtml.exists())
            {
                ctx.getLogger().info("No report HTML generated, but nimble plot had exit code 0");
            }
        }
        else
        {
            ctx.getLogger().info("Only single line found in results, skipping nimble plot");
        }

        return reportResultsGz;
    }

    public static File getReportHtmlFileFromResults(File reportResults)
    {
        return new File(reportResults.getPath().replaceAll("txt(.gz)*$", "html"));
    }

    private static File getNimbleDoneFile(File parentDir, String resumeString)
    {
        return new File(parentDir, "nimble." + resumeString + ".done");
    }

    public static File runFastqToBam(PipelineStepOutput output, PipelineContext ctx, Readset rs, List<File> inputFastqs1, List<File> inputFastqs2, File cellBarcodeUmiMap) throws PipelineJobException
    {
        List<File> outputBams = new ArrayList<>();
        int bamIdx = 0;
        while (bamIdx < inputFastqs1.size())
        {
            File outputBam = new File(ctx.getWorkingDirectory(), FileUtil.makeLegalName(rs.getName()) + ".unmapped." + bamIdx + ".bam");

            List<String> args = new ArrayList<>();
            args.add("python3");
            args.add("-m");
            args.add("nimble");

            args.add("fastq-to-bam");

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                args.add("-c");
                args.add(maxThreads.toString());
            }

            args.add("--r1-fastq");
            args.add(inputFastqs1.get(bamIdx).getPath());
            if (bamIdx > inputFastqs2.size())
            {
                throw new PipelineJobException("Unequal lengths for first/second pair FASTQs");
            }

            args.add("--r2-fastq");
            args.add(inputFastqs2.get(bamIdx).getPath());

            args.add("--map");
            args.add(cellBarcodeUmiMap.getPath());

            args.add("--output");
            args.add(outputBam.getPath());

            runUsingDocker(args, output, "nimble.fastq-to-bam." + bamIdx, ctx);
            outputBams.add(outputBam);
            bamIdx++;
        }

        File outputBam;
        if (outputBams.size() > 1)
        {
            outputBam = new File(ctx.getWorkingDirectory(), FileUtil.makeLegalName(rs.getName()) + ".unmapped.bam");
            outputBams.forEach(output::addIntermediateFile);

            SamtoolsRunner st = new SamtoolsRunner(ctx.getLogger());
            List<String> args = new ArrayList<>(Arrays.asList(st.getSamtoolsPath().getPath(), "merge", "-o", outputBam.getPath(), "-f"));
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                args.add("-@");
                args.add(maxThreads.toString());
            }

            outputBams.forEach(bam -> args.add(bam.getPath()));
            st.execute(args);
        }
        else
        {
            outputBam = outputBams.get(0);
        }

        return outputBam;
    }

    public static String DOCKER_CONTAINER_NAME = "ghcr.io/bimberlab/nimble:latest";

    private boolean runUsingDocker(List<String> nimbleArgs, PipelineStepOutput output, @Nullable String resumeString) throws PipelineJobException
    {
        return runUsingDocker(nimbleArgs, output, resumeString, getPipelineCtx());
    }

    private static boolean runUsingDocker(List<String> nimbleArgs, PipelineStepOutput output, @Nullable String resumeString, PipelineContext ctx) throws PipelineJobException
    {
        DockerWrapper wrapper = new DockerWrapper(DOCKER_CONTAINER_NAME, ctx.getLogger(), ctx);
        wrapper.setWorkingDir(ctx.getWorkingDirectory());
        wrapper.setEntryPoint("/bin/bash");

        wrapper.setTmpDir(null);

        wrapper.addToDockerEnvironment("RUST_BACKTRACE", "1");

        File doneFile = null;
        if (resumeString != null)
        {
            doneFile = getNimbleDoneFile(ctx.getWorkingDirectory(), resumeString);
            output.addIntermediateFile(doneFile);

            if (doneFile.exists())
            {
                ctx.getLogger().info("Nimble already completed, resuming: " + resumeString);
                return false;
            }
            else
            {
                ctx.getLogger().debug("done file not found: " + doneFile.getPath());
            }
        }

        wrapper.executeWithDocker(nimbleArgs, ctx.getWorkingDirectory(), output);

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
                throw new PipelineJobException("Improper genome: " + arr);
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

    private String getVersion(PipelineStepOutput output) throws PipelineJobException
    {
        List<String> nimbleArgs = new ArrayList<>();
        nimbleArgs.add("/bin/bash -c 'python3 -m nimble -v' > nimbleVersion.txt");

        runUsingDocker(nimbleArgs, output, null);

        File outFile = new File(getPipelineCtx().getWorkingDirectory(), "nimbleVersion.txt");
        if (!outFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + outFile.getPath());
        }

        String ret;
        try
        {
            ret = StringUtils.trimToNull(Files.readString(outFile.toPath()));
            if (ret == null)
            {
                throw new PipelineJobException("nimble -v did not output version");
            }
            ret = ret.replaceAll("nimble", "").replaceAll("[\\r\\n]+", "");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        outFile.delete();

        return ret;
    }
}
