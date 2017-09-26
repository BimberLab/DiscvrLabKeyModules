package org.labkey.GeneticsCore.pipeline;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 * Created by bimber on 8/9/2014.
 */
public class BismarkWrapper extends AbstractCommandWrapper
{
    private static final String CONVERTED_GENOME_NAME = "Bisulfite_Genome";
    private static final String METHYLATION_RATES = "Bismark CpG Methylation Rates";
    private static final String RATE_GFF_CATEGORY = "CpG Methylation Rates";

    public BismarkWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BismarkAlignmentStep extends AbstractCommandPipelineStep<BismarkWrapper> implements AlignmentStep
    {
        public BismarkAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BismarkWrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();

            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, CONVERTED_GENOME_NAME, getIndexCachedDirName() + "/" + CONVERTED_GENOME_NAME, referenceGenome);
            BismarkWrapper wrapper = getWrapper();

            List<String> args = new ArrayList<>();
            args.add(wrapper.getExe().getPath());
            
            //NOTE: bismark requires both the index dir and FASTA to be in the same directory.  to sidestep this, make a local dir and create symlinks
            File existingIndexDir = referenceGenome.getAlignerIndexDir(getIndexCachedDirName());
            getPipelineCtx().getLogger().debug("using index from: " + existingIndexDir.getPath());

            try
            {
                File convertedGenome = new File(existingIndexDir, CONVERTED_GENOME_NAME);
                File localIndexDir = new File(outputDirectory, "genome");
                if (localIndexDir.exists())
                {
                    FileUtils.deleteDirectory(localIndexDir);
                }
                localIndexDir.mkdirs();
                output.addIntermediateFile(localIndexDir);

                getPipelineCtx().getLogger().debug("adding Genome and FASTA symlinks");
                Path link1 = Files.createSymbolicLink(new File(localIndexDir, CONVERTED_GENOME_NAME).toPath(), convertedGenome.toPath());
                Path link2 = Files.createSymbolicLink(new File(localIndexDir, referenceGenome.getWorkingFastaFile().getName()).toPath(), referenceGenome.getWorkingFastaFile().toPath());
                //output.addIntermediateFile(link1.toFile());
                //output.addIntermediateFile(link2.toFile());

                args.add(localIndexDir.getPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            args.add("--samtools_path");
            args.add(new SamtoolsRunner(getPipelineCtx().getLogger()).getSamtoolsPath().getParentFile().getPath());
            args.add("--path_to_bowtie");

            //TODO: consider param for bowtie vs. bowtie2
            args.add(getBowtie2Exe().getParentFile().getPath());

            if (getClientCommandArgs() != null)
            {
                args.addAll(getClientCommandArgs());
            }

            args.add("-q"); //input is FASTQ format
            args.add("-o");
            args.add(outputDirectory.getPath());

            Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (threads != null)
            {
                args.add("--multicore"); //multi-threaded
                threads = Math.min(4, threads); //we seem to have intermittent failures due to memory
                args.add(threads.toString());
            }

            args.add("--bam"); //BAM output

            if (inputFastq2 != null)
            {
                args.add("-1");
                args.add(inputFastq1.getPath());

                args.add("-2");
                args.add(inputFastq2.getPath());
            }
            else
            {
                args.add(inputFastq1.getPath());
            }

            String outputBasename = SequenceAnalysisService.get().getUnzippedBaseName(inputFastq1.getName()) + "_bismark_bt2" + (inputFastq2 == null ? "" : "_pe");
            File bam = new File(outputDirectory, outputBasename + ".bam");
            getWrapper().setWorkingDir(outputDirectory);
            getWrapper().execute(args);

            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find BAM: " + bam.getPath());
            }

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
            File report = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputFastq1.getName()) + "_bismark_bt2_" + (inputFastq2 == null ? "SE" : "PE") + "_report.txt");
            output.addOutput(report, "Bismark Summary Report");
            output.addSequenceOutput(report, rs.getName() + ": Bisulfite Conversion Stats", "Bismark Methylation Conversion Stats", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }

        @Override
        public boolean doAddReadGroups()
        {
            return true;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Preparing reference for bismark");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            //always make sure FASTA is in analysis directory
            File localFasta = new File(outputDir, referenceGenome.getWorkingFastaFile().getName());
            File indexOutputDir = localFasta.getParentFile();

            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(), referenceGenome);
            if (!hasCachedIndex)
            {
                if (!localFasta.exists())
                {
                    try
                    {
                        FileUtils.copyFile(referenceGenome.getWorkingFastaFile(), localFasta);
                        output.addIntermediateFile(localFasta);

                        for (String ext : Arrays.asList("dict", "fai"))
                        {
                            File source = new File(referenceGenome.getWorkingFastaFile().getPath() + "." + ext);
                            File dest = new File(localFasta.getPath() + "." + ext);
                            if (source.exists())
                            {
                                FileUtils.copyFile(source, dest);
                                output.addIntermediateFile(dest);
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                //first build for bowtie2
                List<String> args = new ArrayList<>();
                args.add(getWrapper().getBuildExe().getPath());
                args.add("--bowtie2");
                args.add("--path_to_bowtie");
                args.add(getBowtie2Exe().getParentFile().getPath());
                args.add(indexOutputDir.getPath());
                getWrapper().execute(args);

                File genomeBuild = new File(indexOutputDir, CONVERTED_GENOME_NAME);
                File bowtie2TestFile = new File(genomeBuild, "CT_conversion/BS_CT.1.bt2");
                if (!bowtie2TestFile.exists())
                {
                    throw new PipelineJobException("Unable to find file, expected: " + bowtie2TestFile.getPath());
                }

                //then build for bowtie
                List<String> args2 = new ArrayList<>();
                args2.add(getWrapper().getBuildExe().getPath());
                args2.add("--bowtie1");
                args2.add("--path_to_bowtie");
                args2.add(getBowtieExe().getParentFile().getPath());
                args2.add(indexOutputDir.getPath());
                getWrapper().execute(args2);

                File bowtieTestFile = new File(genomeBuild, "CT_conversion/BS_CT.1.ebwt");
                if (!bowtieTestFile.exists())
                {
                    throw new PipelineJobException("Unable to find file, expected: " + bowtieTestFile.getPath());
                }

                File indexBaseDir = new File(localFasta.getParentFile(), getIndexCachedDirName());
                if (!indexBaseDir.exists())
                {
                    indexBaseDir.mkdirs();
                }

                File movedDir = new File(indexBaseDir, genomeBuild.getName());
                try
                {
                    FileUtils.moveDirectory(genomeBuild, movedDir);
                    output.appendOutputs(referenceGenome.getWorkingFastaFile(), movedDir, true);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                //recache if not already
                AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexBaseDir, getIndexCachedDirName(), referenceGenome);
            }

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Bismark", "Bismark is a tool to map bisulfite converted sequence reads and determine cytosine methylation states.  It will use bowtie for the alignment itself.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-L"), "seed_length", "Seed Length", "Sets the length of the seed substrings to align during multiseed alignment. Smaller values make alignment slower but more sensitive.", "ldk-numberfield", null, 30),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-N"), "max_seed_mismatches", "Max Seed Mismatches", "Sets the number of mismatches to be allowed in a seed alignment during multiseed alignment. Can be set to 0 or 1. Setting this higher makes alignment slower (often much slower) but increases sensitivity. Default: 0.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 1)
            ), null, "http://www.bioinformatics.babraham.ac.uk/projects/bismark/", true, false);
        }

        public BismarkAlignmentStep create(PipelineContext context)
        {
            return new BismarkAlignmentStep(this, context);
        }
    }

    public static class BismarkExtractorStep extends AbstractCommandPipelineStep<BismarkWrapper> implements AnalysisStep
    {
        public BismarkExtractorStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BismarkWrapper(ctx.getLogger()));
        }

        @Override
        public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            AnalysisOutputImpl output = new AnalysisOutputImpl();
            BismarkWrapper wrapper = getWrapper();
            try
            {
                String basename = FileUtil.getBaseName(inputBam);
                String outputBasename;
                File queryNameSortBam;
                if (SequencePipelineService.get().getBamSortOrder(inputBam) != SAMFileHeader.SortOrder.queryname)
                {
                    queryNameSortBam = new SamSorter(getPipelineCtx().getLogger()).execute(inputBam, new File(outputDir, basename + ".querySort.bam"), SAMFileHeader.SortOrder.queryname);
                    outputBasename = FileUtil.getBaseName(queryNameSortBam);

                    output.addIntermediateFile(queryNameSortBam);
                }
                else
                {
                    queryNameSortBam = inputBam;
                    outputBasename = basename;
                }

                List<String> args = new ArrayList<>();
                args.add(wrapper.getMethylationExtractorExe().getPath());
                args.add(queryNameSortBam.getPath());

                Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
                if (threads != null)
                {
                    args.add("--multicore"); //multi-threaded
                    args.add(threads.toString());
                }

                args.add("--samtools_path");
                args.add(new SamtoolsRunner(getPipelineCtx().getLogger()).getSamtoolsPath().getParentFile().getPath());

                //paired end vs. single
                if (!rs.hasPairedData())
                {
                    args.add("-s");
                }
                else
                {
                    args.add("-p");
                }

                if (getClientCommandArgs() != null)
                {
                    args.addAll(getClientCommandArgs());
                }

                args.add("-o");
                args.add(outputDir.getPath());

                getWrapper().setWorkingDir(outputDir);
                getWrapper().execute(args);

                //add outputs
                getWrapper().getLogger().debug("using basename: " + outputBasename);
                output.addOutput(new File(outputDir, outputBasename + ".M-bias.txt"), "Bismark M-Bias Report");

                File graph1 = new File(outputDir, outputBasename + ".M-bias_R1.png");
                if (graph1.exists())
                {
                    output.addOutput(graph1, "Bismark M-Bias Image");
                }
                else
                {
                    getPipelineCtx().getLogger().warn("file not found: " + graph1.getPath());
                }

                File graph2 = new File(outputDir, outputBasename + ".M-bias_R2.png");
                if (graph2.exists())
                {
                    output.addOutput(graph2, "Bismark M-Bias Image");
                }
                else
                {
                    getPipelineCtx().getLogger().warn("file not found: " + graph2.getPath());
                }

                output.addOutput(new File(outputDir, outputBasename + ".bam_splitting_report.txt"), "Bismark Splitting Report");

                //NOTE: because the data are likely directional, we will not encounter CTOB
                List<Pair<File, Integer>> CpGmethlyationData = Arrays.asList(
                        Pair.of(new File(outputDir, "CpG_OT_" + outputBasename + ".txt.gz"), 0),
                        Pair.of(new File(outputDir, "CpG_CTOT_" + outputBasename + ".txt.gz"), 0),
                        Pair.of(new File(outputDir, "CpG_OB_" + outputBasename + ".txt.gz"), -1),
                        Pair.of(new File(outputDir, "CpG_CTOB_" + outputBasename + ".txt.gz"), -1)
                );

                List<Pair<File, Integer>> NonCpGmethlyationData = Arrays.asList(
                        Pair.of(new File(outputDir, "NonCpG_OT_" + outputBasename + ".txt.gz"), 0),
                        Pair.of(new File(outputDir, "NonCpG_CTOT_" + outputBasename + ".txt.gz"), 0),
                        Pair.of(new File(outputDir, "NonCpG_OB_" + outputBasename + ".txt.gz"), -1),
                        Pair.of(new File(outputDir, "NonCpG_CTOB_" + outputBasename + ".txt.gz"), -1)
                );

                if (getProvider().getParameterByName("mbias_only") != null && getProvider().getParameterByName("mbias_only").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
                {
                    getPipelineCtx().getJob().getLogger().info("mbias only was selected, no report will be created");
                }
                else if (getProvider().getParameterByName("siteReport") != null && getProvider().getParameterByName("siteReport").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
                {
                    getPipelineCtx().getLogger().info("creating per-site summary report");

                    Integer minCoverageDepth = getProvider().getParameterByName("minCoverageDepth").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
                    File siteReport = new File(outputDir, basename + ".CpG_Site_Summary.methylation.txt");
                    File outputGff = new File(outputDir, basename + ".CpG_Site_Summary.gff");

                    produceSiteReport(getWrapper().getLogger(), siteReport, outputGff, CpGmethlyationData, minCoverageDepth);
                    if (siteReport.exists())
                    {
                        output.addOutput(siteReport, "Bismark CpG Methylation Raw Data");

                        //also try to create an image summarizing:
                        File siteReportPng = createSummaryReport(getWrapper().getLogger(), siteReport, minCoverageDepth);
                        if (siteReportPng != null && siteReportPng.exists())
                        {
                            output.addOutput(siteReportPng, "Bismark CpG Methylation Report");
                            output.addSequenceOutput(siteReportPng, rs.getName() + " methylation rates", "Bismark CpG Methylation Report", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
                        }
                    }

                    if (outputGff.exists())
                    {
                        output.addOutput(outputGff, METHYLATION_RATES);
                        output.addSequenceOutput(outputGff, rs.getName() + " methylation rates (GFF)", RATE_GFF_CATEGORY, rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);
                    }

                    //                File siteReport2 = new File(outputDir, FileUtil.getBaseName(inputBam) + ".NonCpG_Site_Summary.txt");
                    //                File outputGff2 = new File(outputDir, FileUtil.getBaseName(inputBam) + ".NonCpG_Site_Summary.gff");
                    //
                    //                produceSiteReport(getWrapper().getLogger(), siteReport2, outputGff2, NonCpGmethlyationData, minCoverageDepth);
                    //                if (siteReport2.exists())
                    //                {
                    //                    output.addOutput(siteReport2, "Bismark NonCpG Methylation Site Report");
                    //                }
                    //                if (outputGff2.exists())
                    //                {
                    //                    output.addOutput(outputGff2, "Bismark NonCpG Methylation Site Data");
                    //                    output.addSequenceOutput(outputGff2, rs.getName() + " methylation", "NonCpG Methylation Rate Data", rs);
                    //                }

                    //NOTE: if we produce the summary report, assume these are discardable intermediates.  otherwise retain them
                    for (Pair<File, Integer> pair : CpGmethlyationData)
                    {
                        if (pair.first.exists())
                        {
                            output.addIntermediateFile(pair.first, "Bismark Methlyation Site Data");
                        }
                    }

                    for (Pair<File, Integer> pair : NonCpGmethlyationData)
                    {
                        if (pair.first.exists())
                        {
                            output.addIntermediateFile(pair.first, "Bismark Methlyation Site Data");
                        }
                    }
                }
                else
                {
                    getPipelineCtx().getLogger().info("per-site report not selected, skipping");
                    for (Pair<File, Integer> pair : CpGmethlyationData)
                    {
                        if (pair.first.exists())
                        {
                            output.addOutput(pair.first, "Bismark Methlyation Site Data");
                        }
                    }

                    for (Pair<File, Integer> pair : NonCpGmethlyationData)
                    {
                        if (pair.first.exists())
                        {
                            output.addOutput(pair.first, "Bismark Methlyation Site Data");
                        }
                    }
                }

                return output;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
        {
            Integer runId = SequenceAnalysisService.get().getExpRunIdForJob(getPipelineCtx().getJob(), true);

            Set<SequenceOutputFile> toCreate = new HashSet<>();
            SequenceOutputTracker sot = ((SequenceOutputTracker)getPipelineCtx().getJob());
            getPipelineCtx().getLogger().debug("total job outputs pending: " + sot.getOutputsToCreate().size());
            for (SequenceOutputFile so : sot.getOutputsToCreate())
            {
                if (RATE_GFF_CATEGORY.equals(so.getCategory()))
                {
                    toCreate.add(so);
                }
            }

            JSONObject additionalConfig = new JSONObject();
            additionalConfig.put("type", "JBrowse/View/Track/Wiggle/XYPlot");
            additionalConfig.put("max_score", 1);

            TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("outputfiles");
            for (SequenceOutputFile so : toCreate)
            {
                getPipelineCtx().getLogger().debug("processing output: " + so.getName());

                //create output
                SequencePipelineService.get().updateOutputFile(so, getPipelineCtx().getJob(), runId, model.getRowId());
                so = Table.insert(getPipelineCtx().getJob().getUser(), ti, so);

                //then force jbrowse processing
                try
                {
                    getPipelineCtx().getLogger().debug("preparing for JBrowse");
                    JBrowseService.get().prepareOutputFile(getPipelineCtx().getJob().getUser(), getPipelineCtx().getLogger(), so.getRowid(), true, additionalConfig);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                sot.getOutputsToCreate().remove(so);
            }
            getPipelineCtx().getLogger().debug("total job outputs remaining: " + sot.getOutputsToCreate().size());

            return null;
        }

        private File createSummaryReport(Logger log, File siteReport, Integer minCoverageDepth)
        {
            try
            {
                String rScript = getScriptPath();

                List<String> params = new ArrayList<>();
                params.add(getRPath());
                params.add(rScript);
                params.add(siteReport.getPath());
                params.add(minCoverageDepth == null ? "0" : Integer.toString(minCoverageDepth));

                //depth cutoff.  hard-coded for now
                params.add("100");

                getWrapper().execute(params);

                File siteReportPng = new File(FileUtil.getBaseName(siteReport.getPath()) + ".txt.png");
                if (siteReportPng.exists())
                {
                    return siteReportPng;
                }
                else
                {
                    getPipelineCtx().getLogger().warn("unable to find output, expected: " + siteReportPng.getPath());
                }
            }
            catch (PipelineJobException e)
            {
                log.error("Error running R script.  This is probably an R configuration or library issue.  Skipping report", e);
            }

            return null;
        }

        private void produceSiteReport(Logger log, File output, File outputGff, List<Pair<File, Integer>> methlyationData, Integer minCoverageDepth) throws PipelineJobException
        {
            try
            {
                Set<String> keys = new TreeSet<>();

                Map<String, Integer[]> totalMethylatedPerSite = new HashMap<>();
                Map<String, Integer[]> totalNonMethylatedPerSite = new HashMap<>();
                if (minCoverageDepth == null)
                {
                    minCoverageDepth = 0;
                }

                for (Pair<File, Integer> pair : methlyationData)
                {
                    log.info("processing file: " + pair.first.getName());
                    if (!pair.first.exists())
                    {
                        log.warn("file does not exist, skipping: " + pair.first.getName());
                        continue;
                    }

                    try (BufferedReader reader = Readers.getReader(new GZIPInputStream(new FileInputStream(pair.first))))
                    {
                        String line;
                        String[] tokens;

                        int rowIdx = 0;
                        while ((line = reader.readLine()) != null)
                        {
                            rowIdx++;
                            if (rowIdx % 100000 == 0)
                            {
                                log.info("processed " + rowIdx + " lines");
                            }

                            if (rowIdx == 1 || StringUtils.trimToNull(line) == null)
                            {
                                log.debug("skipping line: " + rowIdx);
                                continue;
                            }

                            tokens = StringUtils.split(line, "\t");
                            if (tokens.length != 5)
                            {
                                log.error("incorrect number of items on line, expected 5: " + rowIdx);
                                log.error("[" + StringUtils.join(tokens, "],[") + "]");
                                continue;
                            }

                            Integer pos = ConvertHelper.convert(tokens[3], Integer.class);
                            pos += pair.second;
                            String key = tokens[2] + "||" + StringUtils.leftPad(Integer.toString(pos), 12, '0');
                            keys.add(key);

                            // NOTE: + indicates methylated, not the strand
                            Map<String, Integer[]> totalMap;
                            if ("+".equals(tokens[1]))
                            {
                                totalMap = totalMethylatedPerSite;
                            }
                            else if ("-".equals(tokens[1]))
                            {
                                totalMap = totalNonMethylatedPerSite;
                            }
                            else
                            {
                                log.error("unknown strand: " + tokens[1]);
                                continue;
                            }

                            //the array should be: total encountered, total plus strand, total minus strand,
                            Integer[] arr = totalMap.containsKey(key) ? totalMap.get(key) : new Integer[]{0, 0, 0};
                            arr[0]++;

                            //if the offset is zero, this is a proxy for being + strand.  otherwise it's minus strand
                            if (pair.second.equals(0))
                            {
                                arr[1]++;
                            }
                            else
                            {
                                arr[2]++;
                            }

                            totalMap.put(key, arr);
                        }
                    }
                }

                if (keys.isEmpty())
                {
                    log.info("no positions to report, skipping");
                    return;
                }

                //then write output
                if (!output.exists())
                {
                    log.debug("creating file: " + output.getPath());
                    output.createNewFile();
                }

                if (!outputGff.exists())
                {
                    log.debug("creating file: " + outputGff.getPath());
                    outputGff.createNewFile();
                }

                try (PrintWriter writer = PrintWriters.getPrintWriter(output); PrintWriter gffWriter = PrintWriters.getPrintWriter(outputGff))
                {
                    log.info("writing output, " + keys.size() + " total positions");
                    DecimalFormat df = new DecimalFormat("0.00");

                    writer.write(StringUtils.join(new String[]{"Chr", "Pos", "Depth", "Methylation Rate", "Total Methylated", "Total NonMethylated", "Total Methylated Plus Strand", "Total Methylated Minus Strand", "Total NonMethylated Plus Strand", "Total NonMethylated Minus Strand"}, "\t") + '\n');
                    gffWriter.write("##gff-version 3" + System.getProperty("line.separator"));

                    String[] line;
                    for (String key : keys)
                    {
                        String[] tokens = key.split("\\|\\|");
                        line = new String[10];
                        line[0] = tokens[0];
                        line[1] = Integer.toString(Integer.parseInt(tokens[1]));  //pos, remove leading zeros

                        Integer[] methlyatedArr = totalMethylatedPerSite.containsKey(key) ? totalMethylatedPerSite.get(key) : new Integer[]{0, 0, 0};
                        Integer[] nonMethlyatedArr = totalNonMethylatedPerSite.containsKey(key) ? totalNonMethylatedPerSite.get(key) : new Integer[]{0, 0, 0};

                        int depth = methlyatedArr[0] + nonMethlyatedArr[0];
                        if (depth < minCoverageDepth)
                        {
                            continue;  //skip low coverage to save file size
                        }

                        line[2] = Integer.toString(depth);

                        Double rate = methlyatedArr[0].equals(0) ? 0.0 : ((double) methlyatedArr[0] / (double) depth);
                        line[3] = rate == null ? "" : df.format(rate);

                        line[4] = methlyatedArr[0].equals(0) ? "" : Integer.toString(methlyatedArr[0]);
                        line[5] = nonMethlyatedArr[0].equals(0) ? "" : Integer.toString(nonMethlyatedArr[0]);

                        line[6] = methlyatedArr[1].equals(0) ? "" : Integer.toString(methlyatedArr[1]);
                        line[7] = methlyatedArr[2].equals(0) ? "" : Integer.toString(methlyatedArr[2]);

                        line[8] = nonMethlyatedArr[1].equals(0) ? "" : Integer.toString(nonMethlyatedArr[1]);
                        line[9] = nonMethlyatedArr[2].equals(0) ? "" : Integer.toString(nonMethlyatedArr[2]);

                        writer.write(StringUtils.join(line, '\t') + System.getProperty("line.separator"));

                        String attributes = "Depth=" + depth + ";" +
                                "TotalMethlated=" + line[4] + ";" +
                                "TotalNonMethylated=" + line[5] + ";" +
                                "TotalMethylatedOnPlusStand=" + line[6] + ";" +
                                "TotalMethylatedOnMinusStand=" + line[7] + ";" +
                                "TotalNonMethylatedOnPlusStand=" + line[8] + ";" +
                                "TotalNonMethylatedOnPlusStand=" + line[9] + ";";

                        gffWriter.write(StringUtils.join(new String[]{
                                tokens[0],  //sequence name
                                ".",  //source
                                "site_methylation_rate",  //type
                                line[1],  //start, 1-based
                                line[1],  //end
                                (depth < minCoverageDepth ? "" : Double.toString(rate)),   //score (rate)
                                "+",       //strand
                                "0",       //phase
                                attributes
                        }, '\t') + System.getProperty("line.separator"));
                    }
                }
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
        }

        private String getRPath()
        {
            String exePath = "Rscript";

            //NOTE: this was added to better support team city agents, where R is not in the PATH, but RHOME is defined
            String packagePath = SequencePipelineService.get().inferRPath(getPipelineCtx().getLogger());
            if (StringUtils.trimToNull(packagePath) != null)
            {
                exePath = (new File(packagePath, exePath)).getPath();
            }

            return exePath;
        }

        private String getScriptPath() throws PipelineJobException
        {
            Module module = ModuleLoader.getInstance().getModule(GeneticsCoreModule.class);
            Resource script = module.getModuleResource("/external/methylationBasicStats.R");
            if (!script.exists())
                throw new PipelineJobException("Unable to find file: " + script.getPath());

            File f = ((FileResource) script).getFile();
            if (!f.exists())
                throw new PipelineJobException("Unable to find file: " + f.getPath());

            return f.getPath();
        }
    }

    public static class MethylationExtractorProvider extends AbstractPipelineStepProvider<AnalysisStep>
    {
        public MethylationExtractorProvider()
        {
            super("BismarkMethylationExtractor", "Bismark Methylation Extractor", "Bismark Methylation Extractor", "This step runs the Bismark Methylation Extractor to determine cytosine methylation states.  This step will run against any BAM, but really only makes sense for BAMs created using Bismark upstream.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--merge_non_CpG"), "merge_non_CpG", "Merge non-CpG", "This will produce two output files (in --comprehensive mode) or eight strand-specific output files (default)", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    //always checked
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--gzip"), "gzip", "Compress Outputs", "If checked, the outputs will be compressed to save space", "hidden", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--report"), "report", "Produce Report", "Prints out a short methylation summary as well as the parameters used to run this script.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--mbias_only"), "mbias_only", "M-bias Only", "The methylation extractor will read the entire file but only output the M-bias table and plots as well as a report (optional) and then quit.", "checkbox", null, false),
//                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--bedGraph"), "bedGraph", "Produce BED Graph", "After finishing the methylation extraction, the methylation output is written into a sorted bedGraph file that reports the position of a given cytosine and its methylation state (in %, see details below). The methylation extractor output is temporarily split up into temporary files, one per chromosome (written into the current directory or folder specified with -o/--output); these temp files are then used for sorting and deleted afterwards. By default, only cytosines in CpG context will be sorted. The option '--CX_context' may be used to report all cytosines irrespective of sequence context (this will take MUCH longer!). The default folder for temporary files during the sorting process is the output directory. The bedGraph conversion step is performed by the external module 'bismark2bedGraph'; this script needs to reside in the same folder as the bismark_methylation_extractor itself.", "checkbox", new JSONObject()
//                    {{
//                        put("checked", true);
//                    }}, true),
                    ToolParameterDescriptor.create("siteReport", "Produce Site Summary Report", "If selected, the raw methylation data will be processed to produce a simplified report showing rates per site, rather than per position in the genome.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("minCoverageDepth", "Min Coverage Depth (For Site Report)", "If provided, only sites with at least this coverage depth will be included in the site-based rate calculation.", "ldk-integerfield", null, 10)
            ), null, "http://www.bioinformatics.babraham.ac.uk/projects/bismark/");
        }

        public BismarkExtractorStep create(PipelineContext context)
        {
            return new BismarkExtractorStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark");
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark_genome_preparation");
    }

    protected File getMethylationExtractorExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark_methylation_extractor");
    }

    private static File getBowtie2Exe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIE2PATH", "bowtie2");
    }

    private static File getBowtieExe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIEPATH", "bowtie");
    }
}
