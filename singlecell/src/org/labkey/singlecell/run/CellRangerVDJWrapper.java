package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
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
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CellRangerVDJWrapper extends AbstractCommandWrapper
{
    public CellRangerVDJWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static final String INNER_ENRICHMENT_PRIMERS = "innerEnrichmentPrimers";

    public static class VDJProvider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public VDJProvider()
        {
            super("CellRanger VDJ", "Cell Ranger is an alignment/analysis pipeline specific to 10x genomic data, and this can only be used on fastqs generated by 10x.", Arrays.asList(
                    ToolParameterDescriptor.create("id", "Run ID Suffix", "If provided, this will be appended to the ID of this run (readset name will be first).", "textfield", new JSONObject(){{
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.create(INNER_ENRICHMENT_PRIMERS, "Inner Enrichment Primers", "An option comma-separated list of the inner primers used for TCR enrichment. These will be used for trimming.", "textarea", new JSONObject(){{
                        put("height", 100);
                        put("width", 400);
                    }}, null)
                ), null, "https://support.10xgenomics.com/single-cell-gene-expression/software/pipelines/latest/what-is-cell-ranger", true, false, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public boolean shouldRunIdxstats()
        {
            return false;
        }

        @Override
        public String getName()
        {
            return "CellRanger-VDJ";
        }

        @Override
        public String getDescription()
        {
            return null;
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new CellRangerVDJAlignmentStep(this, context, new CellRangerVDJWrapper(context.getLogger()));
        }
    }

    public static class CellRangerVDJAlignmentStep extends AbstractAlignmentPipelineStep<CellRangerVDJWrapper> implements AlignmentStep
    {
        public CellRangerVDJAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx, CellRangerVDJWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsMetrics()
        {
            return false;
        }

        @Override
        public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            ReferenceGenome referenceGenome = support.getCachedGenomes().iterator().next();
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                getPipelineCtx().getLogger().info("Creating FASTA for CellRanger VDJ Index for genome: " + referenceGenome.getGenomeId());
                File fasta = getGenomeFasta();
                try (PrintWriter writer = PrintWriters.getPrintWriter(fasta))
                {
                    final AtomicInteger i = new AtomicInteger(0);
                    UserSchema us = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), "sequenceanalysis");
                    List<Integer> seqIds = new TableSelector(us.getTable("reference_library_members", null), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), referenceGenome.getGenomeId()), null).getArrayList(Integer.class);
                    new TableSelector(us.getTable("ref_nt_sequences", null), new SimpleFilter(FieldKey.fromString("rowid"), seqIds, CompareType.IN), null).forEach(RefNtSequenceModel.class, nt -> {

                        if (nt.getLocus() == null)
                        {
                            throw new IllegalArgumentException("Locus was empty for NT with ID: " + nt.getRowid());
                        }

                        //NOTE: this allows dual TRA/TRD segments
                        String[] loci = nt.getLocus().split("[/,]");
                        for (String locus : loci)
                        {
                            i.getAndIncrement(); //cant use sequenceId since sequences might be represented multiple times across loci

                            String seq = nt.getSequence();

                            //example: >1|TRAV41*01 TRAV41|TRAV41|L-REGION+V-REGION|TR|TRA|None|None
                            String name = nt.getName();
                            String lineage = nt.getLineage();

                            // Special-case TRAVxx/DVxx lineages:
                            if (lineage.startsWith("TRA") && lineage.contains("DV") && !lineage.contains("/DV"))
                            {
                                if (lineage.contains("-DV"))
                                {
                                    lineage = lineage.replace("-DV", "/DV");
                                }
                                else
                                {
                                    lineage = lineage.replace("DV", "/DV");
                                }
                            }

                            StringBuilder header = new StringBuilder();
                            header.append(">").append(i.get()).append("|").append(name).append(" ").append(lineage).append("|").append(lineage).append("|");

                            //translate into V_Region
                            String type;
                            if (nt.getLineage().contains("J"))
                            {
                                type = "J-REGION";
                            }
                            else if (nt.getLineage().contains("V"))
                            {
                                if (seq.length() < 300)
                                {
                                    getPipelineCtx().getLogger().info("V-segment too short, skipping: " + nt.getName() + " / " + nt.getSeqLength());
                                    continue;
                                }
                                else
                                {
                                    type = "L-REGION+V-REGION";
                                }
                            }
                            else if (nt.getLineage().contains("C"))
                            {
                                type = "C-REGION";
                            }
                            else if (nt.getLineage().contains("D"))
                            {
                                type = "D-REGION";
                            }
                            else
                            {
                                throw new RuntimeException("Unknown lineage: " + nt.getLineage());
                            }

                            header.append(type).append("|TR|").append(locus).append("|None|None");

                            writer.write(header + "\n");
                            writer.write(seq + "\n");
                        }
                        nt.clearCachedSequence();
                    });
                }
                catch (IllegalArgumentException | IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private File getGenomeFasta()
        {
            return new File(getPipelineCtx().getSourceDirectory(), "cellRangerVDJ.fasta");
        }

        @Override
        public String getIndexCachedDirName(PipelineJob job)
        {
            return getProvider().getName();
        }

        @Override
        public AlignmentStep.IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getIndexCachedDirName(getPipelineCtx().getJob()));
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (hasCachedIndex)
            {
                return output;
            }

            getPipelineCtx().getLogger().info("Creating CellRanger VDJ Index");
            getPipelineCtx().getLogger().info("using file: " + getGenomeFasta().getPath());
            output.addIntermediateFile(getGenomeFasta());

            //remove if directory exists
            if (indexDir.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(indexDir);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            output.addInput(getGenomeFasta(), "Input FASTA");

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("mkvdjref");
            args.add("--seqs=" + getGenomeFasta().getPath());
            args.add("--genome=" + indexDir.getName());

            getWrapper().setWorkingDir(indexDir.getParentFile());
            getWrapper().execute(args);

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);

            return output;
        }

        @Override
        public boolean canAlignMultiplePairsAtOnce()
        {
            return true;
        }

        @Override
        public AlignmentStep.AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("multi");
            args.add("--disable-ui");

            String idParam = StringUtils.trimToNull(getProvider().getParameterByName("id").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
            String id = FileUtil.makeLegalName(rs.getName()) + (idParam == null ? "" : "-" + idParam);
            id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");
            args.add("--id=" + id);

            File indexDir = AlignerIndexUtil.getIndexDir(referenceGenome, getIndexCachedDirName(getPipelineCtx().getJob()));

            String primers = StringUtils.trimToNull(getProvider().getParameterByName(INNER_ENRICHMENT_PRIMERS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null));
            File primerFile = new File(outputDirectory, "primers.txt");
            if (primers != null)
            {
                primers = primers.replaceAll("\\s+", ",");
                primers = primers.replaceAll(",+", ",");

                try (PrintWriter writer = PrintWriters.getPrintWriter(primerFile))
                {
                    Arrays.stream(primers.split(",")).forEach(x -> {
                        x = StringUtils.trimToNull(x);
                        if (x != null)
                        {
                            writer.println(x);
                        }
                    });
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                output.addIntermediateFile(primerFile);
            }

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("--localcores=" + maxThreads);
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                args.add("--localmem=" + maxRam);
            }

            File localFqDir = new File(outputDirectory, "localFq");
            output.addIntermediateFile(localFqDir);
            Set<String> sampleNames = prepareFastqSymlinks(rs, localFqDir);

            getPipelineCtx().getLogger().debug("Sample names: [" + StringUtils.join(sampleNames, ",") + "]");

            File sampleFile = new File(getPipelineCtx().getWorkingDirectory(), "sample.csv");
            output.addIntermediateFile(sampleFile);
            try (PrintWriter writer = PrintWriters.getPrintWriter(sampleFile))
            {
                writer.println("[vdj]");
                writer.println("reference," + indexDir.getPath());
                writer.println("inner-enrichment-primers," + primerFile);
                writer.println("");
                writer.println("[libraries]");
                writer.println("fastq_id,fastqs,lanes,feature_types,subsample_rate");
                for (String sampleName : sampleNames)
                {
                    writer.println(sampleName + "," + localFqDir.getPath() + ",,VDJ-T" + ",");
                }

                for (String sampleName : sampleNames)
                {
                    writer.println(sampleName + "-GD" + "," + localFqDir.getPath() + ",,VDJ-T-GD" + ",");
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            args.add("--csv=" + sampleFile.getPath());

            getWrapper().setWorkingDir(outputDirectory);

            //Note: we can safely assume only this server is working on these files, so if the _lock file exists, it was from a previous failed job.
            File lockFile = new File(outputDirectory, id + "/_lock");
            if (lockFile.exists())
            {
                getPipelineCtx().getLogger().info("Lock file exists, deleting: " + lockFile.getPath());
                lockFile.delete();
            }

            getWrapper().execute(args);

            File outdir = new File(outputDirectory, id);
            outdir = new File(outdir, "outs");


            File csvAB = processOutputsForType(id, rs, referenceGenome, outdir, output, "vdj_t");
            File csvGD = processOutputsForType(id, rs, referenceGenome, outdir, output, "vdj_t_gd");

            File combinedCSV = processAndMergeCSVs(csvAB, csvGD, getPipelineCtx().getLogger());

            //NOTE: this folder has many unnecessary files and symlinks that get corrupted when we rename the main outputs
            File directory = new File(outdir.getParentFile(), "SC_MULTI_CS");
            if (directory.exists())
            {
                //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(Arrays.asList("rm", "-Rf", directory.getPath()));
            }
            else
            {
                getPipelineCtx().getLogger().warn("Unable to find folder: " + directory.getPath());
            }

            try
            {
                File outputHtml = new File(outdir, "per_sample_outs/" + id + "/web_summary.html");
                if (!outputHtml.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                }

                File outputHtmlRename = new File(outputHtml.getParentFile(), FileUtil.makeLegalName(rs.getName() + "_") + outputHtml.getName());
                if (outputHtmlRename.exists())
                {
                    outputHtmlRename.delete();
                }
                FileUtils.moveFile(outputHtml, outputHtmlRename);

                output.addSequenceOutput(outputHtmlRename, rs.getName() + " 10x VDJ Summary", "10x Run Summary", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            deleteSymlinks(localFqDir);

            return output;
        }

        private File processOutputsForType(String sampleId, Readset rs, ReferenceGenome referenceGenome, File outdir, AlignmentOutputImpl output, String subdirName) throws PipelineJobException
        {
            boolean isPrimaryDir = "vdj_t".equals(subdirName);
            String chainType = "vdj_t".equals(subdirName) ? "Alpha/Beta" : "Gamma/Delta";

            File multiDir = new File(outdir, "multi/" + subdirName);
            if (!multiDir.exists())
            {
                throw new PipelineJobException("Missing folder: " + multiDir.getPath());
            }

            File sampleDir = new File(outdir, "per_sample_outs/" + sampleId + "/" + subdirName);
            if (!sampleDir.exists())
            {
                throw new PipelineJobException("Missing folder: " + sampleDir.getPath());
            }

            // For simplicity, consolidate all files:
            for (File f : multiDir.listFiles())
            {
                try
                {
                    FileUtils.moveFile(f, new File(sampleDir, f.getName()));
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            File bam = new File(sampleDir, "all_contig.bam");
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bam.getPath());
            }

            if (isPrimaryDir)
            {
                output.setBAM(bam);
            }
            else
            {
                getPipelineCtx().getLogger().debug("Deleting BAM: " + bam.getPath());
                bam.delete();
                new File(bam.getPath() + ".bai").delete();
            }

            File allContigAnnotations = new File(sampleDir, "all_contig_annotations.csv");
            if (!allContigAnnotations.exists())
            {
                throw new PipelineJobException("Missing file: " + allContigAnnotations.getPath());
            }

            File outputVloupe = new File(sampleDir, "vloupe.vloupe");
            File csv = new File(sampleDir, "all_contig_annotations.csv");
            if (!outputVloupe.exists())
            {
                //NOTE: if there were no A/B hits, the vLoupe isnt created, but all other outputs exist
                if (!csv.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputVloupe.getPath());
                }
            }
            // NOTE: only tag the vloupe file for a/b:
            else if (isPrimaryDir)
            {
                output.addSequenceOutput(outputVloupe, rs.getName() + " 10x VLoupe", "10x VLoupe", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
            }

            return csv;
        }

        @Override
        public boolean doAddReadGroups()
        {
            return false;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return false;
        }

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        private String getSymlinkFileName(String sampleName, int idx, boolean isReverseRead)
        {
            return getSymlinkFileName(sampleName, idx, isReverseRead, null);
        }

        private String getSymlinkFileName(String sampleName, int idx, boolean isReverseRead, @Nullable String suffix)
        {
            //NOTE: cellranger is very picky about file name formatting
            sampleName = FileUtil.makeLegalName(sampleName.replaceAll("_", "-")).replaceAll(" ", "-").replaceAll("\\.", "-");
            return sampleName + (suffix == null ? "" : suffix) + "_S1_L001_R" + (isReverseRead ? "2" : "1") + "_" + StringUtils.leftPad(String.valueOf(idx), 3, "0") + ".fastq.gz";
        }

        public Set<String> prepareFastqSymlinks(Readset rs, File localFqDir) throws PipelineJobException
        {
            Set<String> ret = new HashSet<>();
            if (!localFqDir.exists())
            {
                localFqDir.mkdirs();
            }

            String[] files = localFqDir.list();
            if (files != null && files.length > 0)
            {
                deleteSymlinks(localFqDir);
            }

            int idx = 0;
            for (ReadData rd : rs.getReadData())
            {
                idx++;
                try
                {
                    // a/b:
                    File target1 = new File(localFqDir, getSymlinkFileName(rs.getName(), idx, false));
                    getPipelineCtx().getLogger().debug("file: " + rd.getFile1().getPath());
                    getPipelineCtx().getLogger().debug("target: " + target1.getPath());
                    if (target1.exists())
                    {
                        getPipelineCtx().getLogger().debug("deleting existing symlink: " + target1.getName());
                        Files.delete(target1.toPath());
                    }

                    Files.createSymbolicLink(target1.toPath(), rd.getFile1().toPath());
                    ret.add(getSampleName(target1.getName()));

                    // repeat for g/d:
                    File target1gd = new File(localFqDir, getSymlinkFileName(rs.getName(), idx, false, "-GD"));
                    getPipelineCtx().getLogger().debug("file: " + rd.getFile1().getPath());
                    getPipelineCtx().getLogger().debug("target: " + target1gd.getPath());
                    if (target1gd.exists())
                    {
                        getPipelineCtx().getLogger().debug("deleting existing symlink: " + target1gd.getName());
                        Files.delete(target1gd.toPath());
                    }

                    Files.createSymbolicLink(target1gd.toPath(), rd.getFile1().toPath());

                    if (rd.getFile2() != null)
                    {
                        // a/b:
                        File target2 = new File(localFqDir, getSymlinkFileName(rs.getName(), idx, true));
                        getPipelineCtx().getLogger().debug("file: " + rd.getFile2().getPath());
                        getPipelineCtx().getLogger().debug("target: " + target2.getPath());
                        if (target2.exists())
                        {
                            getPipelineCtx().getLogger().debug("deleting existing symlink: " + target2.getName());
                            Files.delete(target2.toPath());
                        }
                        Files.createSymbolicLink(target2.toPath(), rd.getFile2().toPath());
                        ret.add(getSampleName(target2.getName()));

                        // g/d
                        File target2gd = new File(localFqDir, getSymlinkFileName(rs.getName(), idx, true, "-GD"));
                        getPipelineCtx().getLogger().debug("file: " + rd.getFile2().getPath());
                        getPipelineCtx().getLogger().debug("target: " + target2gd.getPath());
                        if (target2gd.exists())
                        {
                            getPipelineCtx().getLogger().debug("deleting existing symlink: " + target2gd.getName());
                            Files.delete(target2gd.toPath());
                        }
                        Files.createSymbolicLink(target2gd.toPath(), rd.getFile2().toPath());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            return ret;
        }

        public void deleteSymlinks(File localFqDir) throws PipelineJobException
        {
            for (File fq : localFqDir.listFiles())
            {
                try
                {
                    getPipelineCtx().getLogger().debug("deleting symlink: " + fq.getName());
                    Files.delete(fq.toPath());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        public void addMetrics(File outDir, AnalysisModel model) throws PipelineJobException
        {
            getPipelineCtx().getLogger().debug("adding 10x metrics");

            File metrics = new File(outDir, "metrics_summary.csv");
            if (!metrics.exists())
            {
                throw new PipelineJobException("Unable to find file: " + metrics.getPath());
            }

            if (model.getAlignmentFile() == null)
            {
                throw new PipelineJobException("model.getAlignmentFile() was null");
            }

            try (CSVReader reader = new CSVReader(Readers.getReader(metrics)))
            {
                String[] line;
                List<String[]> metricValues = new ArrayList<>();

                int i = 0;
                while ((line = reader.readNext()) != null)
                {
                    i++;
                    if (i == 1)
                    {
                        continue;
                    }

                    metricValues.add(line);
                }

                int totalAdded = 0;
                TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

                //NOTE: if this job errored and restarted, we may have duplicate records:
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), model.getReadset());
                filter.addCondition(FieldKey.fromString("analysis_id"), model.getRowId(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("dataid"), model.getAlignmentFile(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("category"), "Cell Ranger VDJ", CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("container"), getPipelineCtx().getJob().getContainer().getId(), CompareType.EQUAL);
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
                if (ts.exists())
                {
                    getPipelineCtx().getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                    ts.getArrayList(Integer.class).forEach(rowid -> {
                        Table.delete(ti, rowid);
                    });
                }

                for (String[] row : metricValues)
                {
                    if ("Fastq ID".equals(row[2]) || "Physical library ID".equals(row[2]))
                    {
                        continue;
                    }

                    Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                    toInsert.put("container", getPipelineCtx().getJob().getContainer().getId());
                    toInsert.put("createdby", getPipelineCtx().getJob().getUser().getUserId());
                    toInsert.put("created", new Date());
                    toInsert.put("readset", model.getReadset());
                    toInsert.put("analysis_id", model.getRowId());
                    toInsert.put("dataid", model.getAlignmentFile());

                    toInsert.put("category", "Cell Ranger VDJ");

                    String mn = row[4];
                    if (Arrays.asList("Cells with productive V-J spanning pair", "Estimated number of cells", "Number of cells with productive V-J spanning pair", "Paired clonotype diversity").contains(mn))
                    {
                        mn = ("VDJ T GD".equals(row[1]) ? "Gamma/Delta" : "Alpha/Beta") + ": " + mn;
                    }
                    toInsert.put("metricname", mn);

                    row[5] = row[5].replaceAll(",", ""); //remove commas
                    Object val = row[5];
                    if (row[5].contains("%"))
                    {
                        row[5] = row[5].replaceAll("%", "");
                        Double d = ConvertHelper.convert(row[5], Double.class);
                        d = d / 100.0;
                        val = d;
                    }

                    toInsert.put("metricvalue", val);

                    Table.insert(getPipelineCtx().getJob().getUser(), ti, toInsert);
                    totalAdded++;
                }

                getPipelineCtx().getLogger().info("total metrics added: " + totalAdded);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void complete(SequenceAnalysisJobSupport support, AnalysisModel model, Collection<SequenceOutputFile> outputFilesCreated) throws PipelineJobException
        {
            if (outputFilesCreated == null || outputFilesCreated.isEmpty())
            {
                throw new PipelineJobException("Expected sequence outputs to be created");
            }

            File html = outputFilesCreated.stream().filter(x -> "10x Run Summary".equals(x.getCategory())).findFirst().orElseThrow().getFile();

            addMetrics(html.getParentFile(), model);

            File bam = model.getAlignmentData().getFile();
            if (!bam.exists())
            {
                getPipelineCtx().getLogger().warn("BAM not found, expected: " + bam.getPath());
            }
        }

        private static final Pattern FILE_PATTERN = Pattern.compile("^(.+?)(_S[0-9]+){0,1}_L(.+?)_(R){0,1}([0-9])(_[0-9]+){0,1}(.*?)(\\.f(ast){0,1}q)(\\.gz)?$");
        private static final Pattern SAMPLE_PATTERN = Pattern.compile("^(.+)_S[0-9]+(.*)$");

        private String getSampleName(String fn)
        {
            Matcher matcher = FILE_PATTERN.matcher(fn);
            if (matcher.matches())
            {
                String ret = matcher.group(1);
                Matcher matcher2 = SAMPLE_PATTERN.matcher(ret);
                if (matcher2.matches())
                {
                    ret = matcher2.group(1);
                }
                else
                {
                    getPipelineCtx().getLogger().debug("_S not found in sample: [" + ret + "]");
                }

                ret = ret.replaceAll("_", "-");

                return ret;
            }
            else
            {
                getPipelineCtx().getLogger().debug("file does not match illumina pattern: [" + fn + "]");
            }

            throw new IllegalArgumentException("Unable to infer Illumina sample name: " + fn);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("CELLRANGERPATH", "cellranger");
    }

    private static File processAndMergeCSVs(File abCSV, File gdCSV, Logger log) throws PipelineJobException
    {
        File output = new File(abCSV.getParentFile(), "all_contig_annotations_combined.csv");

        try (PrintWriter writer = PrintWriters.getPrintWriter(output))
        {
            processCSV(writer, true, abCSV, log, Arrays.asList("TRA", "TRB"), "vdj_t");
            processCSV(writer, false, gdCSV, log, Arrays.asList("TRD", "TRG"), "vdj_t_gd");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }

    private static void processCSV(PrintWriter writer, boolean printHeader, File inputCsv, Logger log, List<String> acceptableChains, String chainType) throws IOException
    {
        log.info("Processing CSV: " + inputCsv.getPath());
        try (BufferedReader reader = Readers.getReader(inputCsv))
        {
            String line;
            int chimericCallsRecovered = 0;

            int lineIdx = 0;
            while ((line = reader.readLine()) != null)
            {
                lineIdx++;
                if (lineIdx == 1)
                {
                    if (printHeader)
                    {
                        writer.println(line + "\tchain_type");
                    }

                    continue;
                }

                //Infer correct chain from the V, J and C genes
                String[] tokens = line.split(",", -1);  // -1 used to preserve trailing empty strings
                List<String> chains = new ArrayList<>();
                String vGeneChain = null;
                String jGeneChain = null;
                String cGeneChain = null;
                for (int idx : new Integer[]{6,8,9})
                {
                    String val = StringUtils.trimToNull(tokens[idx]);
                    if (val != null)
                    {
                        val = val.substring(0, 3);

                        chains.add(val);
                        if (idx == 6)
                        {
                            vGeneChain = val;
                        }
                        if (idx == 8)
                        {
                            jGeneChain = val;
                        }
                        else if (idx == 9)
                        {
                            cGeneChain = val;
                        }
                    }
                }

                Set<String> uniqueChains = new HashSet<>(chains);
                String originalChain = StringUtils.trimToNull(tokens[5]);

                // Recover TRDV/TRAJ/TRAC:
                if (uniqueChains.size() > 1)
                {
                    if (cGeneChain != null)
                    {
                        uniqueChains.clear();
                        uniqueChains.add(cGeneChain);
                        chimericCallsRecovered++;
                    }
                    else if (uniqueChains.size() == 2)
                    {
                        if ("TRD".equals(vGeneChain) && "TRA".equals(jGeneChain))
                        {
                            uniqueChains.clear();
                            chimericCallsRecovered++;
                            uniqueChains.add(vGeneChain);
                        }
                        if ("TRA".equals(vGeneChain) && "TRD".equals(jGeneChain))
                        {
                            uniqueChains.clear();
                            chimericCallsRecovered++;
                            uniqueChains.add(vGeneChain);
                        }
                    }
                }

                if (uniqueChains.size() == 1)
                {
                    String chain = uniqueChains.iterator().next();
                    tokens[5] = chain;
                }
                else
                {
                    log.error("Multiple chains detected [" + StringUtils.join(chains, ",")+ "], leaving original call alone: " + originalChain  + ". " + tokens[6] + "/" + tokens[8] + "/" + tokens[9]);
                }

                if (acceptableChains.contains(tokens[5]))
                {
                    writer.println(StringUtils.join(tokens, ",") + "\t" + chainType);
                }
            }

            log.info("\tChimeric calls recovered: " + chimericCallsRecovered);
        }
    }
}
