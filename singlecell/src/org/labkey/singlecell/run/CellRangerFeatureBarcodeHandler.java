package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.SingleCellSchema;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerFeatureBarcodeHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public static final String HASHING_CATEGORY = "Cell Hashing Counts";
    public static final String CITESEQ_CATEGORY = "CITE-seq Counts";
    private static final String FEATURE_TO_GEX = "FEATURE_TO_GEX";

    public CellRangerFeatureBarcodeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Create CITE-seq/Cell Hashing Counts", "This will run cellranger to generate the raw cite-seq or cell hashing count matrix. It will infer the correct ADT/hashing index sets based on the cDNA library table, and will fail if these readsets are not registered here.", null, Arrays.asList(
            ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, false),
            ToolParameterDescriptor.create("useGEX", "Merge With GEX Data", "To use this option, the readset must be linked to a cDNA record, with GEX data. The reason for this option is that sometimes the GEX fraction can contain valid feature reads.", "checkbox", null, null),
            ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--chemistry"), "chemistry", "Chemistry", "This is usually left blank, in which case cellranger will auto-detect. Example values are: SC3Pv1, SC3Pv2, SC3Pv3, SC5P-PE, SC5P-R2, or SC5P-R1", "textfield", new JSONObject(){{

            }}, null)
        ));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
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
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceReadsetProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (readsets.size() != 1)
            {
                throw new PipelineJobException("Expected jobs to be split and have a single readset as input");
            }

            Readset rs = readsets.get(0);
            if (rs.getApplication() == null)
            {
                throw new PipelineJobException("Readset missing application: " + rs.getRowId());
            }

            List<String> fields = new ArrayList<>();
            boolean failIfNoHashingReadset = false;
            boolean failIfNoCiteseqReadset = false;
            if (isHashing(rs))
            {
                fields.add("hashingReadsetId");
                failIfNoHashingReadset = true;
            }

            if (isCiteSeq(rs))
            {
                fields.add("citeseqReadsetId");
                failIfNoCiteseqReadset = true;
            }

            if (fields.isEmpty())
            {
                throw new PipelineJobException("Unexpected application: " + rs.getApplication());
            }

            for (String field : fields)
            {
                CellHashingServiceImpl.get().prepareHashingAndCiteSeqFilesForFeatureCountsIfNeeded(outputDir, job, support, field, failIfNoHashingReadset, failIfNoCiteseqReadset);

                boolean useGEX = params.optBoolean("useGEX", false);
                if (useGEX)
                {
                    TableInfo cDNATable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), SingleCellSchema.NAME).getTable(SingleCellSchema.TABLE_CDNAS, null);
                    Set<Integer> gexReadsetIds = new HashSet<>(new TableSelector(cDNATable, PageFlowUtil.set("readsetid"), new SimpleFilter(FieldKey.fromString(field), rs.getRowId()), null).getArrayList(Integer.class));
                    if (gexReadsetIds.size() == 1)
                    {
                        support.cacheReadset(gexReadsetIds.iterator().next(), job.getUser());
                        support.cacheObject(FEATURE_TO_GEX, gexReadsetIds.iterator().next());
                    }
                    else
                    {
                        job.getLogger().warn("Expected a single GEX readset for " + rs.getRowId() + ", found: " + gexReadsetIds.size());
                    }
                }
            }
        }

        private boolean isHashing(Readset rs)
        {
            return rs.getApplication().equals("Cell Hashing") || rs.getApplication().equals("Cell Hashing/CITE-seq");
        }

        private boolean isCiteSeq(Readset rs)
        {
            return rs.getApplication().equals("CITE-Seq") || rs.getApplication().equals("Cell Hashing/CITE-seq");
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            if (readsets.size() != 1)
            {
                throw new PipelineJobException("This step was designed to operate on a single readset. It should have been automatically split upstream. Total: " + readsets.size());
            }

            AlignmentOutputImpl output = new AlignmentOutputImpl();

            List<Pair<File, File>> inputFastqs = new ArrayList<>();
            Readset rs = readsets.get(0);
            rs.getReadData().forEach(rd -> {
                inputFastqs.add(Pair.of(rd.getFile1(), rd.getFile2()));
                action.addInputIfNotPresent(rd.getFile1(), "Input FASTQ");
                action.addInputIfNotPresent(rd.getFile2(), "Input FASTQ");
            });

            Integer gexReadsetId = ctx.getSequenceSupport().getCachedObject(FEATURE_TO_GEX, Integer.class);
            if (gexReadsetId != null)
            {
                ctx.getLogger().info("Adding GEX FASTQs");
                Readset gexRS = ctx.getSequenceSupport().getCachedReadset(gexReadsetId);
                gexRS.getReadData().forEach(rd -> {
                    inputFastqs.add(Pair.of(rd.getFile1(), rd.getFile2()));
                    action.addInputIfNotPresent(rd.getFile1(), "Input GEX FASTQ");
                    action.addInputIfNotPresent(rd.getFile2(), "Input GEX FASTQ");
                });
            }

            if (isHashing(rs))
            {
                processType(inputFastqs, output, ctx, HASHING_CATEGORY, createFeatureRefForHashing(ctx.getOutputDir(), CellHashingServiceImpl.get().getAllHashingBarcodesFile(ctx.getSourceDirectory())), rs, (isCiteSeq(rs) ? "-Hashing" : null));

            }

            if (isCiteSeq(rs))
            {
                processType(inputFastqs, output, ctx, CITESEQ_CATEGORY, createFeatureRefForCiteSeq(ctx.getOutputDir(), CellHashingServiceImpl.get().getValidCiteSeqBarcodeMetadataFile(ctx.getSourceDirectory(), rs.getReadsetId())), rs, (isHashing(rs) ? "-CITE" : null));
            }

            ctx.getFileManager().addStepOutputs(action, output);
            ctx.addActions(action);
        }

        private void processType(List<Pair<File, File>> inputFastqs, AlignmentOutputImpl output, JobContext ctx, String category, File featureFile, Readset rs, @Nullable String idSuffix) throws PipelineJobException
        {
            CellRangerWrapper wrapper = new CellRangerWrapper(ctx.getLogger());

            List<String> extraArgs = new ArrayList<>(getClientCommandArgs("=", ctx.getParams()));
            extraArgs.add("--nosecondary");

            extraArgs.add("--feature-ref=" + featureFile.getPath());

            String idParam = ctx.getParams().optString("id", null);
            String id = CellRangerWrapper.getId(idParam, rs);

            List<String> args = wrapper.prepareCountArgs(output, id, ctx.getOutputDir(), rs, inputFastqs, extraArgs, false);

            //https://support.10xgenomics.com/single-cell-gene-expression/software/pipelines/latest/using/feature-bc-analysis
            File libraryCsv = new File(ctx.getOutputDir(), "libraries.csv");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(libraryCsv), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"fastqs", "sample", "library_type"});
                writer.writeNext(new String[]{wrapper.getLocalFastqDir(ctx.getOutputDir()).getPath(), CellRangerWrapper.makeLegalSampleName(rs.getName()), "Antibody Capture"});
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            args.add("--libraries=" + libraryCsv.getPath());

            File indexDir = makeDummyIndex(ctx);
            args.add("--transcriptome=" + indexDir.getPath());

            wrapper.setWorkingDir(ctx.getOutputDir());

            //Note: we can safely assume only this server is working on these files, so if the _lock file exists, it was from a previous failed job.
            File lockFile = new File(ctx.getOutputDir(), id + "/_lock");
            if (lockFile.exists())
            {
                ctx.getLogger().info("Lock file exists, deleting: " + lockFile.getPath());
                lockFile.delete();
            }

            wrapper.execute(args);

            File crDir  = new File(ctx.getOutputDir(), id);
            if (idSuffix != null)
            {
                File toMove = new File(crDir.getPath() + idSuffix);
                ctx.getLogger().debug("Moving cellranger folder to: " + toMove.getPath());
                try
                {
                    FileUtils.moveDirectory(crDir, toMove);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                crDir = toMove;
            }

            File outsdir = new File(crDir, "outs");

            File bam = new File(outsdir, "possorted_genome_bam.bam");
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bam.getPath());
            }
            bam.delete();
            new File(bam.getPath() + ".bai").delete();

            wrapper.deleteSymlinks(wrapper.getLocalFastqDir(ctx.getOutputDir()));

            try
            {
                String prefix = FileUtil.makeLegalName(rs.getName() + "_");
                File outputHtml = new File(outsdir, "web_summary.html");
                if (!outputHtml.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                }

                File outputHtmlRename = new File(outsdir, prefix + outputHtml.getName());
                if (outputHtmlRename.exists())
                {
                    outputHtmlRename.delete();
                }
                FileUtils.moveFile(outputHtml, outputHtmlRename);

                String description = ctx.getParams().optBoolean("useGEX", false) ? "HTO and GEX Counts" : null;
                output.addSequenceOutput(outputHtmlRename, rs.getName() + " 10x " + rs.getApplication() + " Summary", "10x Run Summary", rs.getRowId(), null, null, description);

                File rawCounts = new File(outsdir, "raw_feature_bc_matrix/matrix.mtx.gz");
                if (rawCounts.exists())
                {
                    output.addSequenceOutput(rawCounts, rs.getName() + ": " + rs.getApplication() + " Raw Counts", category, rs.getRowId(), null, null, description);
                    output.addOutput(rawCounts, "Count Matrix");
                }
                else
                {
                    throw new PipelineJobException("Count dir not found: " + rawCounts.getPath());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //NOTE: this folder has many unnecessary files and symlinks that get corrupted when we rename the main outputs
            File directory = new File(outsdir.getParentFile(), "SC_RNA_COUNTER_CS");
            if (directory.exists())
            {
                //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                new SimpleScriptWrapper(ctx.getLogger()).execute(Arrays.asList("rm", "-Rf", directory.getPath()));
            }
            else
            {
                ctx.getLogger().warn("Unable to find folder: " + directory.getPath());
            }
        }

        private File makeDummyIndex(JobContext ctx) throws PipelineJobException
        {
            try
            {
                File indexDir = new File(ctx.getOutputDir(), "cellrangerIndex");
                if (indexDir.exists())
                {
                    FileUtils.deleteDirectory(indexDir);
                }

                File fasta = new File(ctx.getOutputDir(), "genome.fasta");
                try (PrintWriter writer = PrintWriters.getPrintWriter(fasta))
                {
                    writer.println(">1");
                    writer.println("ATGATGATGATGATG");
                }

                File gtf = new File(ctx.getOutputDir(), "genome.gtf");
                try (PrintWriter writer = PrintWriters.getPrintWriter(gtf))
                {
                    writer.println("1\tnowhere\texon\t1\t4\t.\t+\t.\ttranscript_id \"transcript1\"; gene_id \"gene1\"; gene_name \"gene1\";");
                }

                CellRangerWrapper wrapper = new CellRangerWrapper(ctx.getLogger());
                List<String> args = new ArrayList<>();
                args.add(wrapper.getExe(true).getPath());
                args.add("mkref");
                args.add("--fasta=" + fasta.getPath());
                args.add("--genes=" + gtf.getPath());
                args.add("--genome=" + indexDir.getName());

                Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                if (maxThreads != null)
                {
                    args.add("--nthreads=" + maxThreads.toString());
                }

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                if (maxRam != null)
                {
                    args.add("--memgb=" + maxRam.toString());
                }

                wrapper.setWorkingDir(ctx.getOutputDir());
                wrapper.execute(args);

                ctx.getFileManager().addIntermediateFile(fasta);
                ctx.getFileManager().addIntermediateFile(gtf);
                ctx.getFileManager().addIntermediateFile(indexDir);

                return indexDir;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File createFeatureRefForCiteSeq(File outputDir, File adtWhitelist) throws PipelineJobException
        {
            File featuresCsv = new File(outputDir, "adtFeatureRef.csv");
            try (CSVReader reader = new CSVReader(Readers.getReader(adtWhitelist), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(featuresCsv), ',', CSVWriter.DEFAULT_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"id", "name", "read", "pattern", "sequence", "feature_type"});

                //Example: TotalSeq-C-161,CD11b,R2,5PNNNNNNNNNN(BC),GACAAGTGATCTGCA,Antibody Capture
                String[] line;
                int total = 0;
                while ((line = reader.readNext()) != null)
                {
                    if ("tagname".equals(line[0]))
                    {
                        continue;
                    }

                    total++;
                    writer.writeNext(new String[]{line[0], line[2].replaceAll("_", "-"), "R2", line[4], line[1], "Antibody Capture"});
                }

                if (total == 0)
                {
                    throw new PipelineJobException("There were no ADT features!");
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return featuresCsv;
        }

        private File createFeatureRefForHashing(File outputDir, File hashingWhitelist) throws PipelineJobException
        {
            File featuresCsv = new File(outputDir, "hashingFeatureRef.csv");
            try (CSVReader reader = new CSVReader(Readers.getReader(hashingWhitelist), ',');CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(featuresCsv), ',', CSVWriter.DEFAULT_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"id", "name", "read", "pattern", "sequence", "feature_type"});
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    writer.writeNext(new String[]{line[1], line[1], "R2", line[3], line[0], "Antibody Capture"});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return featuresCsv;
        }

        @Override
        public void complete(PipelineJob job, List<Readset> readsets, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            if (outputsCreated.isEmpty())
            {
                job.getLogger().error("Expected outputs to be created");
                return;
            }

            SequenceOutputFile so = null;
            for (SequenceOutputFile o : outputsCreated)
            {
                if ("10x Run Summary".equals(o.getCategory()))
                {
                    continue;
                }

                so = o;
                break;
            }

            if (so == null)
            {
                throw new PipelineJobException("Unable to find count matrix as output");
            }

            Readset rs = readsets.get(0);

            File metrics = new File(so.getFile().getParentFile().getParentFile(), "metrics_summary.csv");
            if (metrics.exists())
            {
                job.getLogger().debug("adding 10x metrics");
                try (CSVReader reader = new CSVReader(Readers.getReader(metrics)))
                {
                    String[] line;
                    String[] header = null;
                    String[] metricValues = null;

                    int i = 0;
                    while ((line = reader.readNext()) != null)
                    {
                        if (i == 0)
                        {
                            header = line;
                        }
                        else
                        {
                            metricValues = line;
                            break;
                        }

                        i++;
                    }

                    TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

                    //NOTE: if this job errored and restarted, we may have duplicate records:
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), so.getReadset());
                    filter.addCondition(FieldKey.fromString("dataid"), so.getDataId(), CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("category"), rs.getApplication(), CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("container"), job.getContainer().getId(), CompareType.EQUAL);
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
                    if (ts.exists())
                    {
                        job.getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                        ts.getArrayList(Integer.class).forEach(rowid -> {
                            Table.delete(ti, rowid);
                        });
                    }

                    for (int j = 0; j < header.length; j++)
                    {
                        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                        toInsert.put("container", job.getContainer().getId());
                        toInsert.put("createdby", job.getUser().getUserId());
                        toInsert.put("created", new Date());
                        toInsert.put("readset", rs.getReadsetId());
                        toInsert.put("dataid", so.getDataId());

                        toInsert.put("category", "Cell Ranger");
                        toInsert.put("metricname", header[j]);

                        metricValues[j] = metricValues[j].replaceAll(",", "");
                        Object val = metricValues[j];
                        if (metricValues[j].contains("%"))
                        {
                            metricValues[j] = metricValues[j].replaceAll("%", "");
                            Double d = ConvertHelper.convert(metricValues[j], Double.class);
                            d = d / 100.0;
                            val = d;
                        }

                        toInsert.put("metricvalue", val);

                        Table.insert(job.getUser(), ti, toInsert);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                job.getLogger().warn("unable to find metrics file: " + metrics.getPath());
            }
        }
    }
}
