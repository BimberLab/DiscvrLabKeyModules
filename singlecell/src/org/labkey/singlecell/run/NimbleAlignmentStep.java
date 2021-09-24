package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.SingleCellModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NimbleAlignmentStep extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public static final String REF_GENOMES = "refGenomes";
    public static final String ALIGN_TEMPLATE = "alignTemplate";
    public static final String GROUP_BY_LINEAGE = "groupByLineage";

    public NimbleAlignmentStep()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Nimble", "This will run Nimble to generate a supplemental feature count matrix for the provided libraries", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeField.js")), Arrays.asList(
                ToolParameterDescriptor.create(REF_GENOMES, "Reference Genome(s)", null, "sequenceanalysis-genomefield", new JSONObject(){{
                    put("multiSelect", true);
                }}, null),
                ToolParameterDescriptor.create(ALIGN_TEMPLATE, "Alignment Template", "This will select a pre-defined set of alignment config options", "ldk-simplecombo", new JSONObject(){{
                    put("allowBlank", false);
                    put("storeValues", "strict;lenient");
                    put("initialValues", "strict");
                    put("delimiter", ";");
                }}, null),
                ToolParameterDescriptor.create(GROUP_BY_LINEAGE, "Group By Lineage", "If checked, results will be aggregated by lineage", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        ));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    private static final FileType BAM = new FileType("bam");

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && BAM.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputHandler.SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            List<Integer> genomeIds = getGenomeIds(ctx);
            for (int id : genomeIds)
            {
                prepareGenome(id, ctx);
            }
        }

        private List<Integer> getGenomeIds(JobContext ctx) throws PipelineJobException
        {
            String genomeStr = StringUtils.trimToNull(ctx.getParams().optString(REF_GENOMES, null));
            if (genomeStr == null)
            {
                throw new PipelineJobException("Missing genomes");
            }

            return Arrays.stream(genomeStr.split(";")).map(Integer::valueOf).collect(Collectors.toList());
        }
        private void prepareGenome(int id, JobContext ctx) throws PipelineJobException
        {
            ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(id, ctx.getJob().getUser());
            if (rg == null)
            {
                throw new PipelineJobException("Unable to find genome: " + id);
            }

            ctx.getSequenceSupport().cacheGenome(rg);
            ctx.getLogger().info("Preparing genome CSV/FASTA for " + rg.getName());
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(getGenomeCsv(id, ctx)), ',', CSVWriter.NO_QUOTE_CHARACTER);PrintWriter fastaWriter = PrintWriters.getPrintWriter(getGenomeFasta(id, ctx)))
            {
                writer.writeNext(new String[]{"reference_genome", "name", "nt_length", "genbank", "category", "subset", "locus", "lineage", "sequence"});

                Container targetFolder = ctx.getJob().getContainer().isWorkbook() ? ctx.getJob().getContainer().getParent() : ctx.getJob().getContainer();
                TableInfo ti = QueryService.get().getUserSchema(ctx.getJob().getUser(), targetFolder, "sequenceanalysis").getTable("reference_library_members");
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("ref_nt_id"), FieldKey.fromString("ref_nt_id/name"), FieldKey.fromString("ref_nt_id/seqLength"), FieldKey.fromString("ref_nt_id/genbank"), FieldKey.fromString("ref_nt_id/category"), FieldKey.fromString("ref_nt_id/subset"), FieldKey.fromString("ref_nt_id/locus"), FieldKey.fromString("ref_nt_id/lineage")));
                TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("library_id"), id), null);
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

        private File getGenomeCsv(int id, JobContext ctx)
        {
            return new File(ctx.getSourceDirectory(), "genome." + id + ".csv");
        }

        private File getGenomeFasta(int id, JobContext ctx)
        {
            return new File(ctx.getSourceDirectory(), "genome." + id + ".fasta");
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            SequenceOutputFile so = inputFiles.get(0);
            List<Integer> genomeIds = getGenomeIds(ctx);
            for (int genomeId : genomeIds)
            {
                File genomeCsv = getGenomeCsv(genomeId, ctx);
                File genomeFasta = getGenomeFasta(genomeId, ctx);

                File refJson = prepareReference(ctx, genomeCsv, genomeFasta);
                File results = doAlignment(ctx, genomeId, refJson, so);

                ctx.getFileManager().addIntermediateFile(genomeCsv);
                ctx.getFileManager().addIntermediateFile(genomeFasta);
                ctx.getFileManager().addIntermediateFile(refJson);

                ctx.getFileManager().addSequenceOutput(results, so.getName() + ": nimble align", "Nimble Alignment", so.getReadset(), null, genomeId, null);
            }
        }

        private File prepareReference(JobContext ctx, File genomeCsv, File genomeFasta) throws PipelineJobException
        {
            genomeCsv = ensureLocalCopy(genomeCsv, ctx);
            genomeFasta = ensureLocalCopy(genomeFasta, ctx);

            File nimbleJson = new File(ctx.getWorkingDirectory(), FileUtil.getBaseName(genomeFasta) + ".json");
            runUsingDocker(ctx, Arrays.asList("generate", "/work/" + genomeFasta.getName(), "/work/" + genomeCsv.getName(), "/work/" + nimbleJson.getName()));
            if (!nimbleJson.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + nimbleJson.getPath());
            }

            updateNimbleConfigFile(ctx, nimbleJson);

            return nimbleJson;
        }

        private void updateNimbleConfigFile(JobContext ctx, File configFile) throws PipelineJobException
        {
            JSONArray json;
            try (BufferedReader reader = Readers.getReader(configFile);StringBuilderWriter writer = new StringBuilderWriter();)
            {
                IOUtils.copy(reader, writer);
                json = new JSONArray(writer.toString());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            JSONObject config = json.getJSONObject(0);
            ctx.getLogger().info("Initial config:");
            ctx.getLogger().info(config.toString(1));
            try (PrintWriter writer = PrintWriters.getPrintWriter(configFile))
            {
                String alignTemplate = ctx.getParams().optString(ALIGN_TEMPLATE, "lenient");
                if ("lenient".equals(alignTemplate))
                {
                    config.put("num_mismatches", 10);
                    config.put("intersect_level", 0);
                    config.put("score_threshold", 25);
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

                boolean groupByLineage = ctx.getParams().optBoolean(GROUP_BY_LINEAGE, false);
                if (groupByLineage)
                {
                    config.put("group_on", "lineage");
                }

                ctx.getLogger().info("Final config:");
                ctx.getLogger().info(config.toString(1));

                json.put(0, config);
                IOUtils.write(json.toString(), writer);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File doAlignment(JobContext ctx, int genomeId, File refJson, SequenceOutputFile so) throws PipelineJobException
        {
            File localBam = ensureLocalCopy(so.getFile(), ctx);
            ensureLocalCopy(new File(so.getFile().getPath() + ".bai"), ctx);

            File localRefJson = ensureLocalCopy(refJson, ctx);
            File resultsTsv = new File(ctx.getWorkingDirectory(), "results." + genomeId + ".txt");

            List<String> alignArgs = new ArrayList<>();
            alignArgs.add("align");
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                alignArgs.add("-c");
                alignArgs.add(String.valueOf(maxThreads));
            }

            alignArgs.add("-l");
            alignArgs.add("/work/nimbleDebug.txt");

            alignArgs.add("/work/" + localRefJson.getName());
            alignArgs.add("/work/" + resultsTsv.getName());
            alignArgs.add("/work/" + localBam.getName());

            runUsingDocker(ctx, alignArgs);
            if (!resultsTsv.exists())
            {
                throw new PipelineJobException("Expected to find file: " + resultsTsv.getPath());
            }

            File log = new File(resultsTsv.getParentFile(), "nimbleDebug.txt");
            if (!log.exists())
            {
                throw new PipelineJobException("Expected to find file: " + log.getPath());
            }

            ctx.getLogger().info("Nimble alignment stats:");
            try (BufferedReader reader = Readers.getReader(log))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    ctx.getLogger().info(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            log.delete();

            return resultsTsv;
        }

        public static String DOCKER_CONTAINER_NAME = "ghcr.io/bimberlab/nimble:latest";

        private void runUsingDocker(JobContext ctx, List<String> nimbleArgs) throws PipelineJobException
        {
            File localBashScript = new File(ctx.getWorkingDirectory(), "docker.sh");
            ctx.getFileManager().addIntermediateFile(localBashScript);

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
                writer.println("");
                writer.println("echo 'Bash script complete'");
                writer.println("");
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(ctx.getLogger());
            rWrapper.setWorkingDir(ctx.getOutputDir());
            rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));
        }

        private File ensureLocalCopy(File input, JobContext ctx) throws PipelineJobException
        {
            try
            {
                if (ctx.getWorkingDirectory().equals(input.getParentFile()))
                {
                    return input;
                }

                File local = new File(ctx.getWorkingDirectory(), input.getName());
                if (!local.exists())
                {
                    ctx.getLogger().debug("Copying file locally: " + input.getPath());
                    FileUtils.copyFile(input, local);
                }

                ctx.getFileManager().addIntermediateFile(local);

                return local;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
