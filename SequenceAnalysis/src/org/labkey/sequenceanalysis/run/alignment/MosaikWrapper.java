package org.labkey.sequenceanalysis.run.alignment;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
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
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 12/14/12
 * Time: 7:40 AM
 */
public class MosaikWrapper extends AbstractCommandWrapper
{
    private static final String MFL = "median_fragment_length";
    private static final String LOCAL_SEARCH_RADIUS = "local_search_radius";

    public MosaikWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class MosaikAlignmentStep extends AbstractAlignmentPipelineStep<MosaikWrapper> implements AlignmentStep
    {
        public MosaikAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new MosaikWrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return false;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            File outputFile = getWrapper().getExpectedMosaikRefFile(indexDir, referenceGenome.getWorkingFastaFile());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }
                getWrapper().executeMosaikBuild(referenceGenome.getWorkingFastaFile(), null, outputFile, "-oa", null);
                if (!outputFile.exists())
                    throw new PipelineJobException("Unable to find file: " + outputFile.getPath());
            }

            output.addOutput(outputFile, IndexOutputImpl.PRIMARY_ALIGNER_INDEX_FILE);
            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), referenceGenome);

            return output;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);
            File inputFastq2 = assertSingleFile(inputFastqs2);

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), referenceGenome);
            getWrapper().setOutputDir(outputDirectory);

            String mfl = StringUtils.trimToNull(getProvider().getParameterByName(MFL).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
            String localAlignmentRadius = StringUtils.trimToNull(getProvider().getParameterByName(LOCAL_SEARCH_RADIUS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));

            //TODO: can we infer the technology?
            File reads = getWrapper().buildFastqReads(outputDirectory, inputFastq1, inputFastq2, SequencingTechnology.illumina_long, mfl);
            output.addIntermediateFile(reads);

            List<String> params = new ArrayList<>();
            Set<String> blacklist = new HashSet<>(PageFlowUtil.set(MFL));
            if (mfl == null)
            {
                if (localAlignmentRadius != null)
                {
                    getPipelineCtx().getLogger().warn("local search radius supplied without median fragment length and will be ignored.");
                }

                blacklist.add(LOCAL_SEARCH_RADIUS);
            }

            params.addAll(getClientCommandArgs(" ", blacklist));
            if (SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob()) != null)
            {
                params.add("-p");
                params.add(SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob()).toString());
            }

            File bam = getWrapper().executeMosaikAligner(referenceGenome.getWorkingFastaFile(), referenceGenome.getAlignerIndexDir(getProvider().getName()), reads, outputDirectory, basename, params);
            if (!bam.exists())
            {
                throw new PipelineJobException("BAM not created, expected: " + bam.getPath());
            }

            output.setBAM(bam);
            output.addIntermediateFile(new File(outputDirectory, basename + ".stat"));
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
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
////            'ma|mode' => 'm',

            super("Mosaik", "Mosaik is suitable for longer reads and has the option to retain multiple hits per read. The only downside is that it can be slower. When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-om"), "output_multiple", "Retain All Hits", "If selected, all hits above thresholds will be reported. If not, only a single hit will be retained", "checkbox", new JSONObject()
                    {{
                            put("checked", true);
                        }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mmp"), "max_mismatch_pct", "Max Mismatch Pct", "The maximum percent of bases allowed to mismatch per alignment. Note: Ns are counted as mismatches", "ldk-numberfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 1);
                        }}, 0.02),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-hs"), "hash_size", "Hash Size", "The hash size used in alignment (see Mosaik documentation). A large value is preferred for sequences expected to be highly similar to the reference", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 32);
                        }}, 15),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mhp"), "max_hash_positions", "Max Hash Positions", "The maximum number of hash matches that are passed to local alignment", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 300);
                        }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-act"), "align_threshold", "Alignment Threshold", "The alignment score (length) required for an alignment to continue to local alignment. Because the latter is slow, a higher value can improve speed", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-bw"), "banded_smith_waterman", "Use Banded Smith-Waterman", "Uses the banded Smith-Waterman algorithm for increased performance", "ldk-integerfield", null, 51),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mfl"), MFL, "Median Fragment Length", "This is used by Mosaik for local alignments to rescue paired reads.  If median fragment length is 200bp, and the local search radius  is set to 100bp, then Mosaik will search 100-300bp downstream for a proper mate alignment.  This can be useful for PCR amplicons.", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-ls"), LOCAL_SEARCH_RADIUS, "Local Search Radius", "This is used by Mosaik for local alignments to rescue paired reads.  If median fragment length is 200bp, and the local search radius is set to 100bp, then Mosaik will search 100-300bp downstream for a proper mate alignment.  This can be useful for PCR amplicons.", "ldk-integerfield", null, 150)
                    ), null, "https://code.google.com/p/mosaik-aligner/", true, true);
        }

        public MosaikAlignmentStep create(PipelineContext context)
        {
            return new MosaikAlignmentStep(this, context);
        }
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("MOSAIKPATH", "MosaikBuild");
    }

    protected File getAlignExe()
    {
        return SequencePipelineService.get().getExeForPackage("MOSAIKPATH", "MosaikAligner");
    }

    public enum SequencingTechnology
    {
        roche454("454"),
        helicos("helicos"),
        illumina("illumina"),
        illumina_long("illumina_long"),
        sanger("sanger"),
        solid("solid");

        private String _paramValue;

        SequencingTechnology(String paramValue)
        {
            _paramValue = paramValue;
        }

        public String getParamValue()
        {
            return _paramValue;
        }
    }

    private File buildFastqReads(File outputDirectory, File input, @Nullable File input2, SequencingTechnology technology, @Nullable String mfl) throws PipelineJobException
    {
        File output = new File(outputDirectory, FileUtil.getBaseName(input) + ".mosaikreads");

        List<String> options = new ArrayList<>();
        options.add("-st");
        options.add(technology.getParamValue());
        if (mfl != null)
        {
            options.add("-mfl");
            options.add(mfl);
        }

        executeMosaikBuild(input, input2, output, "-out", options);
        if (!output.exists())
            throw new PipelineJobException("Unable to find file: " + output.getPath());

        return output;
    }

    public File executeMosaikAligner(File refFasta, File indexDir, File reads, File outputDirectory, String basename, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getAlignExe().getPath());
        args.add("-ia");
        File mosaikRef = getExpectedMosaikRefFile(indexDir, refFasta);
        if (!mosaikRef.exists())
        {
            throw new PipelineJobException("Unable to find reference file: " + mosaikRef.getPath());
        }
        args.add(mosaikRef.getPath());

        args.add("-in");
        args.add(reads.getPath());

        args.add("-out");
        args.add(new File(outputDirectory, basename).getPath());

        File networkDir = SequencePipelineService.get().getExeForPackage("MOSAIK_NETWORK_FILE", "mosaikNetworkFile");
        args.add("-annpe");
        args.add(new File(networkDir, "2.1.78.pe.ann").getPath());
        args.add("-annse");
        args.add(new File(networkDir, "2.1.78.se.ann").getPath());
        args.add("-quiet");

        args.addAll(options);

        execute(args);

        try
        {
            File bam = new File(outputDirectory, basename + ".bam");
            File multipleHitsBam = new File(outputDirectory, basename + ".multiple.bam");

            //this switch makes mosaik output all hits
            boolean retainAll = args.contains("-om");
            if (retainAll)
            {
                //merge BAMs
                int mergedRecords = 0;
                int singleRecords = 0;
                int multipleRecords = 0;

                SamReaderFactory fact = SamReaderFactory.makeDefault();
                fact.validationStringency(ValidationStringency.SILENT);
                fact.referenceSequence(refFasta);
                File mergedBam = new File(outputDirectory, basename + ".merged.bam");
                try (SamReader bamReader = fact.open(bam);SamReader multipleReader = fact.open(multipleHitsBam))
                {
                    SAMFileWriterFactory writerFactory = new SAMFileWriterFactory();
                    writerFactory.setUseAsyncIo(true);
                    writerFactory.setCompressionLevel(9);
                    SAMFileHeader header = bamReader.getFileHeader().clone();
                    try (SAMFileWriter writer = writerFactory.makeBAMWriter(header, false, mergedBam))
                    {
                        //this depends on the two files being in identical queryName sort, also with forward before reverse
                        Iterator<SAMRecord> bamIt = bamReader.iterator();
                        Iterator<SAMRecord> multipleIt = multipleReader.iterator();

                        Set<String> forwardKeys = new HashSet<>();
                        Set<String> reverseKeys = new HashSet<>();
                        while (bamIt.hasNext())
                        {
                            SAMRecord r1 = bamIt.next();
                            writer.addAlignment(r1);
                            singleRecords++;
                            mergedRecords++;

                            String key = r1.getReadName() + "<>" + r1.getContig() + "<>" + r1.getCigarString();
                            if (!r1.getReadPairedFlag() || r1.getFirstOfPairFlag())
                            {
                                forwardKeys.add(key);
                            }
                            else
                            {
                                reverseKeys.add(key);
                            }
                        }

                        while (multipleIt.hasNext())
                        {
                            SAMRecord r1 = multipleIt.next();
                            multipleRecords++;

                            String key = r1.getReadName() + "<>" + r1.getContig() + "<>" + r1.getCigarString();
                            if (!r1.getReadPairedFlag() || r1.getFirstOfPairFlag())
                            {
                                if (!forwardKeys.contains(key))
                                {
                                    writer.addAlignment(r1);
                                    mergedRecords++;
                                }
                            }
                            else
                            {
                                if (!reverseKeys.contains(key))
                                {
                                    writer.addAlignment(r1);
                                    mergedRecords++;
                                }
                            }
                        }
                    }
                }

                getLogger().info("total records in primary BAM: " + singleRecords);
                getLogger().info("total records in multiple hits BAM: " + multipleRecords);
                getLogger().info("total records in merged BAM: " + mergedRecords);

                bam.delete();
                File idx = new File(bam.getPath() + ".bai");
                if (idx.exists())
                {
                    idx.delete();
                }
                multipleHitsBam.delete();
                idx = new File(multipleHitsBam.getPath() + ".bai");
                if (idx.exists())
                {
                    idx.delete();
                }

                bam = mergedBam;
            }
            else
            {
                getLogger().info("using single hits BAM");
                if (multipleHitsBam.exists())
                {
                    multipleHitsBam.delete();
                }
            }

            //Note: mosaik has started to give errors about bin field
            //FixBAMWrapper fixBam = new FixBAMWrapper(getLogger());
            //fixBam.executeCommand(bam, null);

            new SamSorter(getLogger()).execute(bam, null, SAMFileHeader.SortOrder.coordinate);

            return bam;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public void executeMosaikBuild(File input1, @Nullable File input2, File output, String outParam, @Nullable List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getBuildExe().getPath());
        if (options != null)
        {
            args.addAll(options);
        }

        args.add("-quiet");

        SequenceUtil.FILETYPE type = SequenceUtil.inferType(input1);
        String paramName1;
        String paramName2;
        if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            paramName1 = "-fr";
            paramName2 = "-f2";
        }
        else if (type.equals(SequenceUtil.FILETYPE.fastq))
        {
            paramName1 = "-q";
            paramName2 = "-q2";
        }
        else
        {
            throw new IllegalArgumentException("Unknown input type: " + type.name());
        }

        CommandLineParam arg1 = CommandLineParam.create(paramName1);
        args.addAll(arg1.getArguments(input1.getPath()));

        if (input2 != null)
        {
            CommandLineParam arg2 = CommandLineParam.create(paramName2);
            args.addAll(arg2.getArguments(input2.getPath()));
        }

        CommandLineParam outArg = CommandLineParam.create(outParam);
        args.addAll(outArg.getArguments(output.getPath()));

        execute(args);
    }

    public File getExpectedMosaikRefFile(File outputDir, File refFasta)
    {
        return new File(outputDir, FileUtil.getBaseName(refFasta) + ".mosaik");
    }
}
