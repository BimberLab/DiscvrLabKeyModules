package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CellRangerFeatureBarcodeHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public static final String HASHING_CATEGORY = "Cell Hashing Counts";
    public static final String CITESEQ_CATEGORY = "CITE-seq Counts";

    public CellRangerFeatureBarcodeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Create CITE-seq/Cell Hashing Counts", "This will run cellranger to generate the raw cite-seq or cell hashing count matrix. It will infer the correct ADT/hashing index sets based on the cDNA library table, and will fail if these readsets are not registered here.", null, Arrays.asList(
            ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, false)
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

            String field;
            boolean failIfNoHashing = false;
            boolean failIfNoCiteseq = false;
            if (rs.getApplication().equals("Cell Hashing"))
            {
                field = "hashingReadsetId";
                failIfNoHashing = true;
            }
            else if (rs.getApplication().equals("CITE-Seq"))
            {
                field = "citeseqReadsetId";
                failIfNoCiteseq = true;
            }
            else
            {
                throw new PipelineJobException("Unexpected application: " + rs.getApplication());
            }

            CellHashingServiceImpl.get().prepareHashingAndCiteSeqFilesIfNeeded(outputDir, job, support, field, failIfNoHashing, failIfNoCiteseq, false);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            if (readsets.size() != 1)
            {
                throw new PipelineJobException("This step was designed to operate on a single readset. It should have been automatically split upstream. Total: " + readsets.size());
            }

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            CellRangerWrapper wrapper = new CellRangerWrapper(ctx.getLogger());

            Readset rs = readsets.get(0);

            String category;
            File featureFile;
            if (rs.getApplication().equals("Cell Hashing"))
            {
                category = HASHING_CATEGORY;
                featureFile = createFeatureRefForHashing(ctx.getOutputDir(), CellHashingServiceImpl.get().getAllHashingBarcodesFile(ctx.getSourceDirectory()));

            }
            else if (rs.getApplication().equals("CITE-Seq"))
            {
                category = CITESEQ_CATEGORY;
                featureFile = createFeatureRefForCiteSeq(ctx.getOutputDir(), CellHashingServiceImpl.get().getValidCiteSeqBarcodeMetadataFile(ctx.getSourceDirectory(), rs.getReadsetId()));
            }
            else
            {
                throw new IllegalStateException("Unknown category. This should be caught upstream");
            }

            List<String> extraArgs = new ArrayList<>(getClientCommandArgs("=", ctx.getParams()));
            extraArgs.add("--nosecondary");

            extraArgs.add("--feature-ref=" + featureFile.getPath());

            String idParam = ctx.getParams().optString("id", null);
            String id = CellRangerWrapper.getId(idParam, rs);

            List<Pair<File, File>> inputFastqs = new ArrayList<>();
            rs.getReadData().forEach(rd -> {
                inputFastqs.add(Pair.of(rd.getFile1(), rd.getFile2()));
            });

            List<String> args = wrapper.prepareCountArgs(output, id, ctx.getOutputDir(), rs, inputFastqs, extraArgs, false);

            //https://support.10xgenomics.com/single-cell-gene-expression/software/pipelines/latest/using/feature-bc-analysis
            File libraryCsv = new File(ctx.getOutputDir(), "libraries.csv");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(libraryCsv), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"fastqs", "sample", "library_type"});
                writer.writeNext(new String[]{wrapper.getLocalFastqDir(ctx.getOutputDir()).getPath(), wrapper.makeLegalSampleName(rs.getName()), "Antibody Capture"});
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

            File outdir = new File(ctx.getOutputDir(), id);
            outdir = new File(outdir, "outs");

            File bam = new File(outdir, "possorted_genome_bam.bam");
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
                File outputHtml = new File(outdir, "web_summary.html");
                if (!outputHtml.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                }

                File outputHtmlRename = new File(outdir, prefix + outputHtml.getName());
                if (outputHtmlRename.exists())
                {
                    outputHtmlRename.delete();
                }
                FileUtils.moveFile(outputHtml, outputHtmlRename);
                output.addSequenceOutput(outputHtmlRename, rs.getName() + " 10x " + rs.getApplication() + " Summary", "10x Run Summary", rs.getRowId(), null, null, null);

                File rawCounts = new File(outdir, "raw_feature_bc_matrix/matrix.mtx.gz");
                if (rawCounts.exists())
                {
                    output.addSequenceOutput(rawCounts, rs.getName() + ": " + rs.getApplication() + " Raw Counts", category, rs.getRowId(), null, null, null);
                }
                else
                {
                    ctx.getLogger().info("Count dir not found: " + rawCounts.getPath());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //NOTE: this folder has many unnecessary files and symlinks that get corrupted when we rename the main outputs
            File directory = new File(outdir.getParentFile(), "SC_RNA_COUNTER_CS");
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
                while ((line = reader.readNext()) != null)
                {
                    if ("tagname".equals(line[0]))
                    {
                        continue;
                    }

                    writer.writeNext(new String[]{line[0], line[2].replaceAll("_", "-"), "R2", line[4], line[1], "Antibody Capture"});
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
                    //TODO: allow database to pass pattern
                    writer.writeNext(new String[]{line[1], line[1], "R2", "5P(BC)", line[0], "Antibody Capture"});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return featuresCsv;
        }


    }
}
