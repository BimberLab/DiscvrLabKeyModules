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
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
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
    public static final String INCLUDE_GD = "includeGD";

    public static class VDJProvider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public VDJProvider()
        {
            super("CellRanger VDJ", "Cell Ranger is an alignment/analysis pipeline specific to 10x genomic data, and this can only be used on fastqs generated by 10x.", Arrays.asList(
                    //--sample

                    ToolParameterDescriptor.create("id", "Run ID Suffix", "If provided, this will be appended to the ID of this run (readset name will be first).", "textfield", new JSONObject(){{
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--force-cells"), "force-cells", "Force Cells", "Force pipeline to use this number of cells, bypassing the cell detection algorithm. Use this if the number of cells estimated by Cell Ranger is not consistent with the barcode rank plot.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--disable-ui"), "disable-ui", "Disable UI", "If checked, this will run cellranger with the optional web-based UI disabled.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create(INNER_ENRICHMENT_PRIMERS, "Inner Enrichment Primers", "An option comma-separated list of the inner primers used for TCR enrichment. These will be used for trimming.", "textarea", new JSONObject(){{
                        put("height", 100);
                        put("width", 400);
                    }}, null),
                    ToolParameterDescriptor.create(INCLUDE_GD, "Include G/D", "As a hack to get CellRanger 6 to include g/d (which it does not normally allow), they can be included in the reference, but marked as TRA/TRB. Alignment is performed with these values, and the result is converted in the final text file.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
                    ), null, "https://support.10xgenomics.com/single-cell-gene-expression/software/pipelines/latest/what-is-cell-ranger", true, false, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public boolean shouldRunIdxstats()
        {
            return false;
        }

        public String getName()
        {
            return "CellRanger-VDJ";
        }

        public String getDescription()
        {
            return null;
        }

        public AlignmentStep create(PipelineContext context)
        {
            return new CellRangerVDJAlignmentStep(this, context, new CellRangerVDJWrapper(context.getLogger()));
        }
    }

    public static class CellRangerVDJAlignmentStep extends AbstractAlignmentPipelineStep<CellRangerVDJWrapper> implements AlignmentStep
    {
        public CellRangerVDJAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, CellRangerVDJWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsMetrics()
        {
            return false;
        }

        private boolean doGDParsing()
        {
            return getProvider().getParameterByName(INCLUDE_GD).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        }

        @Override
        public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            ReferenceGenome referenceGenome = support.getCachedGenomes().iterator().next();
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                getPipelineCtx().getLogger().info("Creating FASTA for CellRanger VDJ Index for genome: " + referenceGenome.getName());
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
                            //Note: for g/d recovery, add any gamma segments as TRA and delta as TRB. Also prefix their gene names with TRA/B, but keep the true name (i.e. TRDV1 -> TRBTRDV1)
                            String prefix = "";
                            if (doGDParsing() && "TRD".equalsIgnoreCase(locus))
                            {
                                prefix = "TRB";
                                locus = "TRB";
                            }
                            else if (doGDParsing() && "TRG".equalsIgnoreCase(locus))
                            {
                                prefix = "TRA";
                                locus = "TRA";
                            }

                            StringBuilder header = new StringBuilder();
                            header.append(">").append(i.get()).append("|").append(prefix).append(nt.getName()).append(" ").append(prefix).append(nt.getLineage()).append("|").append(prefix).append(nt.getLineage()).append("|");
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
                                    getPipelineCtx().getLogger().info("Using V-REGION due to short length: " + nt.getName() + " / " + nt.getSeqLength());
                                    type = "V-REGION";
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
            return getProvider().getName() + (doGDParsing() ? "-GD" : "");
        }

        @Override
        public AlignmentStep.IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getIndexCachedDirName(getPipelineCtx().getJob()));
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
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
                args.add(getWrapper().getExe(doGDParsing()).getPath());
                args.add("mkvdjref");
                args.add("--seqs=" + getGenomeFasta().getPath());
                args.add("--genome=" + indexDir.getName());

                getWrapper().setWorkingDir(indexDir.getParentFile());
                getWrapper().execute(args);

                output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

                //recache if not already
                AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            }

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
            args.add(getWrapper().getExe(doGDParsing()).getPath());
            args.add("vdj");

            String idParam = StringUtils.trimToNull(getProvider().getParameterByName("id").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
            String id = FileUtil.makeLegalName(rs.getName()) + (idParam == null ? "" : "-" + idParam);
            id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");
            args.add("--id=" + id);

            File indexDir = AlignerIndexUtil.getIndexDir(referenceGenome, getIndexCachedDirName(getPipelineCtx().getJob()));
            args.add("--reference=" + indexDir.getPath());

            String primers = StringUtils.trimToNull(getProvider().getParameterByName(INNER_ENRICHMENT_PRIMERS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null));
            if (primers != null)
            {
                primers = primers.replaceAll("\\s+", ",");
                primers = primers.replaceAll(",+", ",");

                File primerFile = new File(outputDirectory, "primers.txt");
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
                args.add("--inner-enrichment-primers=" + primerFile.getPath());
            }

            args.addAll(getClientCommandArgs("="));

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("--localcores=" + maxThreads.toString());
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                args.add("--localmem=" + maxRam.toString());
            }

            File localFqDir = new File(outputDirectory, "localFq");
            output.addIntermediateFile(localFqDir);
            Set<String> sampleNames = prepareFastqSymlinks(rs, localFqDir);
            args.add("--fastqs=" + localFqDir.getPath());

            getPipelineCtx().getLogger().debug("Sample names: [" + StringUtils.join(sampleNames, ",") + "]");
            if (sampleNames.size() > 1)
            {
                args.add("--sample=" + StringUtils.join(sampleNames, ","));
            }

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

            File bam = new File(outdir, "all_contig.bam");
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bam.getPath());
            }
            output.setBAM(bam);

            //now do cleanup/rename:
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

                output.addSequenceOutput(outputHtmlRename, rs.getName() + " 10x VDJ Summary", "10x Run Summary", rs.getRowId(), null, referenceGenome.getGenomeId(), null);

                File outputVloupe = new File(outdir, "vloupe.vloupe");
                File csv = new File(outdir, "all_contig_annotations.csv");
                if (!outputVloupe.exists())
                {
                    //NOTE: if there were no A/B hits, the vLoupe isnt created, but all other outputs exist
                    if (!csv.exists())
                    {
                        throw new PipelineJobException("Unable to find file: " + outputVloupe.getPath());
                    }
                }
                else
                {
                    File outputVloupeRename = new File(outdir, prefix + outputVloupe.getName());
                    if (outputVloupeRename.exists())
                    {
                        outputVloupeRename.delete();
                    }
                    FileUtils.moveFile(outputVloupe, outputVloupeRename);
                    output.addSequenceOutput(outputVloupeRename, rs.getName() + " 10x VLoupe", "10x VLoupe", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
                }

                if (doGDParsing())
                {
                    getPipelineCtx().getLogger().info("Removing g/d prefixes from all_contig_annotations.csv file");
                    File csv2 = new File(outdir, "all_contig_annotations2.csv");
                    try (PrintWriter writer = PrintWriters.getPrintWriter(csv2); BufferedReader reader = Readers.getReader(csv))
                    {
                        String line;
                        int totalD = 0;
                        int totalG = 0;
                        while ((line = reader.readLine()) != null)
                        {
                            if (line.contains("TRATRG") || line.contains("TRBTRD"))
                            {
                                //Infer correct chain from the V, J and C genes
                                String[] tokens = line.split(",");
                                List<String> chains = new ArrayList<>();
                                String cGeneChain = null;
                                for (int idx : new Integer[]{6,8,9}) {
                                    String val = StringUtils.trimToNull(tokens[idx]);
                                    if (val != null)
                                    {
                                        if (val.contains("TRATRG"))
                                        {
                                            val = "TRG";
                                        }
                                        else if (val.contains("TRBTRD"))
                                        {
                                            val = "TRD";
                                        }
                                        else
                                        {
                                            val = val.substring(0, 3);
                                        }

                                        chains.add(val);
                                        if (idx == 9)
                                        {
                                            cGeneChain = val;
                                        }
                                    }
                                }

                                Set<String> uniqueChains = new HashSet<>(chains);
                                String originalChain = StringUtils.trimToNull(tokens[5]);

                                // Recover TRDV/TRAJ/TRAC:
                                if (uniqueChains.size() > 1 && cGeneChain != null)
                                {
                                    uniqueChains.clear();
                                    uniqueChains.add(cGeneChain);
                                }

                                if (uniqueChains.size() == 1)
                                {
                                    String chain = uniqueChains.iterator().next();
                                    if (chain.equals("TRG"))
                                    {
                                        if (!originalChain.equals("TRA") && !"None".equals(originalChain))
                                        {
                                            getPipelineCtx().getLogger().error("Unexpected chain: was " + originalChain + ", to " + chain);
                                        }

                                        totalG++;
                                    }
                                    else if (chain.equals("TRD"))
                                    {
                                        if (!originalChain.equals("TRB") && !"None".equals(originalChain))
                                        {
                                            getPipelineCtx().getLogger().error("Unexpected chain: was " + originalChain + ", to " + chain);
                                        }

                                        totalD++;
                                    }

                                    tokens[5] = chain;

                                    line = StringUtils.join(tokens, ",");
                                    line = line.replaceAll("TRATRG", "TRG");
                                    line = line.replaceAll("TRBTRD", "TRD");
                                }
                                else
                                {
                                    getPipelineCtx().getLogger().warn("Multiple chains detected [" + StringUtils.join(chains, ",")+ "], leaving original call alone: " + originalChain);
                                }
                            }

                            writer.println(line);
                        }

                        getPipelineCtx().getLogger().info("\tTotal TRA->TRG changes: " + totalG);
                        getPipelineCtx().getLogger().info("\tTotal TRB->TRD changes: " + totalD);
                    }

                    //TODO: ultimately remove this and delete file
                    FileUtils.moveFile(csv, new File(csv.getPath() + ".orig"));
                    FileUtils.moveFile(csv2, csv);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //NOTE: this folder has many unnecessary files and symlinks that get corrupted when we rename the main outputs
            File directory = new File(outdir.getParentFile(), "SC_VDJ_ASSEMBLER_CS");
            if (directory.exists())
            {
                //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(Arrays.asList("rm", "-Rf", directory.getPath()));
            }
            else
            {
                getPipelineCtx().getLogger().warn("Unable to find folder: " + directory.getPath());
            }

            deleteSymlinks(localFqDir);

            return output;
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

        private String getSymlinkFileName(String fileName, boolean doRename, String sampleName, int idx, boolean isReversed)
        {
            //NOTE: cellranger is very picky about file name formatting
            if (doRename)
            {
                sampleName = FileUtil.makeLegalName(sampleName.replaceAll("_", "-")).replaceAll(" ", "-").replaceAll("\\.", "-");;
                return sampleName + "_S1_L001_R" + (isReversed ? "2" : "1") + "_" + StringUtils.leftPad(String.valueOf(idx), 3, "0") + ".fastq.gz";
            }
            else
            {
                Matcher m = FILE_PATTERN.matcher(fileName);
                if (m.matches())
                {
                    if (!StringUtils.isEmpty(m.group(7)))
                    {
                        return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                    }
                    else if (m.group(1).contains("_"))
                    {
                        getPipelineCtx().getLogger().info("replacing underscores in file/sample name");
                        return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                    }
                    else
                    {
                        getPipelineCtx().getLogger().info("no additional characters found");
                    }
                }
                else
                {
                    getPipelineCtx().getLogger().warn("filename does not match Illumina formatting: " + fileName);
                }
            }

            return FileUtil.makeLegalName(fileName);
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
            boolean doRename = true;  //cellranger is too picky - simply rename files all the time
            for (ReadData rd : rs.getReadData())
            {
                idx++;
                try
                {
                    File target1 = new File(localFqDir, getSymlinkFileName(rd.getFile1().getName(), doRename,  rs.getName(), idx, false));
                    getPipelineCtx().getLogger().debug("file: " + rd.getFile1().getPath());
                    getPipelineCtx().getLogger().debug("target: " + target1.getPath());
                    if (target1.exists())
                    {
                        getPipelineCtx().getLogger().debug("deleting existing symlink: " + target1.getName());
                        Files.delete(target1.toPath());
                    }

                    Files.createSymbolicLink(target1.toPath(), rd.getFile1().toPath());
                    ret.add(getSampleName(target1.getName()));

                    if (rd.getFile2() != null)
                    {
                        File target2 = new File(localFqDir, getSymlinkFileName(rd.getFile2().getName(), doRename, rs.getName(), idx, true));
                        getPipelineCtx().getLogger().debug("file: " + rd.getFile2().getPath());
                        getPipelineCtx().getLogger().debug("target: " + target2.getPath());
                        if (target2.exists())
                        {
                            getPipelineCtx().getLogger().debug("deleting existing symlink: " + target2.getName());
                            Files.delete(target2.toPath());
                        }
                        Files.createSymbolicLink(target2.toPath(), rd.getFile2().toPath());
                        ret.add(getSampleName(target2.getName()));
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

        public void addMetrics(AnalysisModel model) throws PipelineJobException
        {
            getPipelineCtx().getLogger().debug("adding 10x metrics");

            File metrics = new File(model.getAlignmentFileObject().getParentFile(), "metrics_summary.csv");
            if (metrics.exists())
            {
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

                    for (int j = 0; j < header.length; j++)
                    {
                        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                        toInsert.put("container", getPipelineCtx().getJob().getContainer().getId());
                        toInsert.put("createdby", getPipelineCtx().getJob().getUser().getUserId());
                        toInsert.put("created", new Date());
                        toInsert.put("readset", model.getReadset());
                        toInsert.put("analysis_id", model.getRowId());
                        toInsert.put("dataid", model.getAlignmentFile());

                        toInsert.put("category", "Cell Ranger VDJ");
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
            else
            {
                getPipelineCtx().getLogger().warn("unable to find metrics file: " + metrics.getPath());
            }
        }

        public void complete(SequenceAnalysisJobSupport support, AnalysisModel model) throws PipelineJobException
        {
            addMetrics(model);

            File bam = model.getAlignmentData().getFile();
            if (!bam.exists())
            {
                getPipelineCtx().getLogger().warn("BAM not found, expected: " + bam.getPath());
            }
        }

        private static Pattern FILE_PATTERN = Pattern.compile("^(.+?)(_S[0-9]+){0,1}_L(.+?)_(R){0,1}([0-9])(_[0-9]+){0,1}(.*?)(\\.f(ast){0,1}q)(\\.gz)?$");
        private static Pattern SAMPLE_PATTERN = Pattern.compile("^(.+)_S[0-9]+(.*)$");

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

    protected File getExe(boolean includeGD)
    {
        //NOTE: cellranger 4 doesnt work w/ custom libraries currently. update to CR4 when fixed
        return SequencePipelineService.get().getExeForPackage("CELLRANGERPATH", includeGD ? "cellranger" : "cellranger-31");
    }
}
