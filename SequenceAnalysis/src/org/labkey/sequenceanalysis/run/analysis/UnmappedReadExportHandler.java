package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import htsjdk.samtools.util.CloserUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.DNASequence;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.alignment.FastqCollapser;
import org.labkey.sequenceanalysis.run.util.FlashWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class UnmappedReadExportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _fileType = new FileType(".bam", FileType.gzSupportLevel.NO_GZ);

    public UnmappedReadExportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Export Unmapped Reads", "This will export unmapped reads from each BAM to create FASTQ or FASTA files.  If FASTA is selected, it will also merge all reads into a single FASTA, collapsing identical reads together.", null, Arrays.asList(
                ToolParameterDescriptor.create("fastaExport", "Export As FASTA", "If selected, the unmapped reads will be exported as a FASTA file, rather than the default FASTQ.", "checkbox", null, null),
                ToolParameterDescriptor.create("joinForwardReverse", "Join Forward/Reverse Reads", "If selected, the forward and reverse reads will be joined using FLASH to attempt to make a single longer sequence.", "checkbox", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _fileType.isType(f.getFile());
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                File inputBam = so.getFile();
                String basename = FileUtil.getBaseName(inputBam);

                boolean fastaExport = ctx.getParams().optBoolean("fastaExport", false);
                boolean joinForwardReverse = ctx.getParams().optBoolean("joinForwardReverse", false);

                File unmappedReadsF = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_F.fastq");
                File unmappedReadsR = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_R.fastq");
                File singletons = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_singletons.fastq");

                writeUnmappedReadsAsFastq(inputBam, unmappedReadsF, unmappedReadsR, singletons, ctx.getLogger());
                long unmappedReadsFCount = SequenceUtil.getLineCount(unmappedReadsF);
                ctx.getLogger().info("total first mate unmapped reads: " + (unmappedReadsFCount / 4));
                if (unmappedReadsFCount == 0)
                {
                    unmappedReadsF.delete();
                }
                else if (!joinForwardReverse)
                {
                    ctx.getFileManager().addSequenceOutput(unmappedReadsF, "Unmapped first mate reads: " + inputBam.getName(), "Unmapped Reads", so.getReadset(), so.getAnalysis_id(), so.getLibrary_id(), null);
                }
                else
                {
                    ctx.getFileManager().addIntermediateFile(unmappedReadsF);
                }

                long unmappedReadsRCount = SequenceUtil.getLineCount(unmappedReadsR);
                ctx.getLogger().info("total second mate unmapped reads: " + (unmappedReadsRCount / 4));
                if (unmappedReadsRCount == 0)
                {
                    unmappedReadsR.delete();
                }
                else if (!joinForwardReverse)
                {
                    ctx.getFileManager().addSequenceOutput(unmappedReadsR, "Unmapped second mate reads: " + inputBam.getName(), "Unmapped Reads", so.getReadset(), so.getAnalysis_id(), so.getLibrary_id(), null);
                }
                else
                {
                    ctx.getFileManager().addIntermediateFile(unmappedReadsR);
                }

                long singletonsCount = SequenceUtil.getLineCount(singletons);
                ctx.getLogger().info("total unpaired, unmapped reads: " + (singletonsCount / 4));
                if (singletonsCount == 0)
                {
                    singletons.delete();
                }
                else if (!joinForwardReverse)
                {
                    ctx.getFileManager().addSequenceOutput(singletons, "Unmapped singleton reads: " + inputBam.getName(), "Unmapped Reads", so.getReadset(), so.getAnalysis_id(), so.getLibrary_id(), null);
                }
                else
                {
                    ctx.getFileManager().addIntermediateFile(singletons);
                }

                List<File> toCollapse = new ArrayList<>();
                if (joinForwardReverse)
                {
                    //join reads
                    FlashWrapper flash = new FlashWrapper(ctx.getLogger());
                    File joined = flash.execute(unmappedReadsF, unmappedReadsR, ctx.getOutputDir(), basename + ".joined", null);
                    if (joined.exists() && SequenceUtil.hasMinLineCount(joined, 2))
                    {
                        toCollapse.add(joined);
                    }

                    File notJoinedF = new File(ctx.getOutputDir(), basename + ".joined.notCombined_1.fastq");
                    if (notJoinedF.exists() && SequenceUtil.hasMinLineCount(notJoinedF, 2))
                    {
                        toCollapse.add(notJoinedF);
                    }

                    File notJoinedR = new File(ctx.getOutputDir(), basename + ".joined.notCombined_2.fastq");
                    if (notJoinedR.exists() && SequenceUtil.hasMinLineCount(notJoinedR, 2))
                    {
                        toCollapse.add(notJoinedR);
                    }
                }
                else
                {
                    toCollapse.add(unmappedReadsF);
                    toCollapse.add(unmappedReadsR);
                    toCollapse.add(singletons);
                }

                File merged = new File(ctx.getOutputDir(), basename + ".unmapped.fastq.gz");
                try (PrintWriter writer = PrintWriters.getPrintWriter(merged))
                {
                    for (File f : toCollapse)
                    {
                        ctx.getLogger().debug("processing file: " + f.getName());
                        if (!f.exists())
                        {
                            continue;
                        }

                        try (BufferedReader reader = Readers.getReader(f))
                        {
                            String line;
                            while ((line = reader.readLine()) != null)
                            {
                                writer.write(line);
                            }
                        }

                        f.delete();
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                //collapse reads
                FastqCollapser collapser = new FastqCollapser(ctx.getLogger());
                File collapsed = new File(ctx.getOutputDir(), basename + ".collapsed.fastq");
                collapser.collapseFile(merged, collapsed);

                ctx.getFileManager().addSequenceOutput(singletons, "Unmapped Reads: " + inputBam.getName(), "Unmapped Reads", so.getReadset(), so.getAnalysis_id(), so.getLibrary_id(), null);
            }
        }

        public static void writeUnmappedReadsAsFastq(File inputBam, File paired1, File paired2, File singletons, Logger log) throws PipelineJobException
        {
            FastqWriterFactory fact = new FastqWriterFactory();
            fact.setUseAsyncIo(true);
            try (FastqWriter paired1Writer = fact.newWriter(paired1); FastqWriter paired2Writer = fact.newWriter(paired2); FastqWriter singletonsWriter = fact.newWriter(singletons))
            {
                SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
                try (SamReader reader = samReaderFactory.open(inputBam); SamReader mateReader = samReaderFactory.open(inputBam))
                {
                    try (SAMRecordIterator it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            SAMRecord r = it.next();
                            if (r.getReadUnmappedFlag())
                            {
                                //pairs where both unmapped
                                if (r.getReadPairedFlag() && r.getMateUnmappedFlag())
                                {
                                    if (r.getFirstOfPairFlag())
                                    {
                                        SAMRecord mate = mateReader.queryMate(r);
                                        if (mate != null)
                                        {
                                            FastqRecord fq = samReadToFastqRecord(r, "/1");
                                            paired1Writer.write(fq);

                                            FastqRecord fq2 = samReadToFastqRecord(mate, "/2");
                                            paired2Writer.write(fq2);
                                        }
                                        else
                                        {
                                            log.error("Unable to find mate for read: " + r.getReadName() + ", " + r.getContig());
                                            FastqRecord fq = samReadToFastqRecord(r, null);
                                            singletonsWriter.write(fq);
                                        }
                                    }
                                    else
                                    {
                                        //we assume we wrote this read above when we encountered the mapped first of mate
                                    }
                                }
                                //singlets or paired with single read unmapped
                                else
                                {
                                    FastqRecord fq = samReadToFastqRecord(r, null);
                                    singletonsWriter.write(fq);
                                }

                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private static FastqRecord samReadToFastqRecord(SAMRecord read, @Nullable String readNameSuffix)
        {
            String bases = read.getReadString();
            String qualities = read.getBaseQualityString();

            if (read.getReadNegativeStrandFlag())
            {
                try
                {
                    DNASequence seq = new DNASequence(bases);
                    bases = seq.getReverseComplement().getSequenceAsString();

                    qualities = StringUtils.reverse(qualities);
                }
                catch (CompoundNotFoundException e)
                {
                    throw new IllegalArgumentException("Improper DNA string: " + bases, e);
                }

            }

            String readName = read.getReadName();
            if (readNameSuffix != null && !readName.endsWith(readNameSuffix))
            {
                readName = readName + readNameSuffix;
            }

            return new FastqRecord(readName, bases, null, qualities);
        }

        public static List<File> writeUnmappedReadsAsFasta(File inputBam, File fasta, Logger log, @Nullable Long maxReads, @Nullable Integer lineLength) throws PipelineJobException
        {
            log.info("writing unmapped reads to FASTA");

            List<File> ret = new ArrayList<>();
            ret.add(fasta);

            List<PrintWriter> writers = new ArrayList<>();
            try
            {
                PrintWriter writer = PrintWriters.getPrintWriter(fasta);
                writers.add(writer);

                long readsEncountered = 0;
                SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
                try (SamReader reader = samReaderFactory.open(inputBam))
                {
                    try (SAMRecordIterator it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            SAMRecord r = it.next();
                            if (r.getReadUnmappedFlag())
                            {
                                readsEncountered++;

                                if (maxReads != null)
                                {
                                    if (readsEncountered % maxReads == 0)
                                    {
                                        File newFile = new File(fasta.getParentFile(), FileUtil.getBaseName(fasta) + "-" + ret.size());
                                        log.debug("splitting into new file after " + readsEncountered + " reads: " + newFile.getName());

                                        ret.add(newFile);

                                        writer = PrintWriters.getPrintWriter(newFile);
                                        writers.add(writer);
                                    }
                                }

                                String name = r.getReadName();
                                if (r.getReadPairedFlag())
                                {
                                    name = name + (r.getFirstOfPairFlag() ? "/1" : "/2");
                                }

                                SequenceUtil.writeFastaRecord(writer, name, r.getReadString(), (lineLength == null ? 9999999 : lineLength));
                            }
                        }
                    }
                }

                log.info("total unmapped reads: " + readsEncountered);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                for (Writer w : writers)
                {
                    CloserUtil.close(w);
                }
            }

            return ret;
        }
    }
}