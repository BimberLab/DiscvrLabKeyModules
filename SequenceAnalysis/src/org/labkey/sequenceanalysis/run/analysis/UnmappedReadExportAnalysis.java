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
import org.apache.logging.log4j.LogManager;
import org.biojava3.core.sequence.DNASequence;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.run.alignment.FastqCollapser;
import org.labkey.sequenceanalysis.util.SequenceUtil;

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
public class UnmappedReadExportAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public UnmappedReadExportAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<UnmappedReadExportAnalysis>
    {
        public Provider()
        {
            super("UnalignedReadExport", "Export Unmapped Reads", null, "This will export unmapped reads from each BAM to create FASTQ files.", Arrays.asList(
                    ToolParameterDescriptor.create("fastaExport", "Export As FASTA", "If selected, the unmapped reads will be exported as a FASTA file, rather than the default FASTQ.", "checkbox", null, null)
            ), null, null);
        }

        @Override
        public UnmappedReadExportAnalysis create(PipelineContext ctx)
        {
            return new UnmappedReadExportAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return null;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        boolean fastaExport= getProvider().getParameterByName("fastaExport").extractValue(getPipelineCtx().getJob(), this.getProvider(), getStepIdx(), Boolean.class, false);
        String extension = fastaExport ? "fasta" : "fastq";

        if (fastaExport)
        {
            File fasta = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped." + extension);

            writeUnmappedReadsAsFasta(inputBam, fasta, getPipelineCtx().getLogger(), null, null);
            if (SequenceUtil.getLineCount(fasta) == 0)
            {
                fasta.delete();
            }
            else
            {
                output.addIntermediateFile(fasta);

                File collapsed = new File(fasta.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped.collapsed." + extension);
                FastqCollapser collapser = new FastqCollapser(getPipelineCtx().getLogger());
                collapser.collapseFile(fasta, collapsed);
                long collapsedLineCount = SequenceUtil.getLineCount(collapsed);
                getPipelineCtx().getLogger().info("total collapsed FASTA sequences: " + (collapsedLineCount / 2));

                output.addSequenceOutput(collapsed, "Unmapped reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId(), null);
            }
        }
        else
        {
            File paired1 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_paired1." + extension);
            File paired2 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_paired2." + extension);
            File singletons = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_unmapped_singletons." + extension);

            writeUnmappedReadsAsFastq(inputBam, paired1, paired2, singletons, getPipelineCtx().getLogger());
            long paired1Count = SequenceUtil.getLineCount(paired1);
            getPipelineCtx().getLogger().info("total first mate unmapped reads: " + (paired1Count / 4));
            if (paired1Count == 0)
            {
                paired1.delete();
            }
            else
            {
                output.addSequenceOutput(paired1, "Unmapped first mate reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId(), null);
            }

            long paired2Count = SequenceUtil.getLineCount(paired2);
            getPipelineCtx().getLogger().info("total second mate unmapped reads: " + (paired2Count / 4));
            if (paired2Count == 0)
            {
                paired2.delete();
            }
            else
            {
                output.addSequenceOutput(paired2, "Unmapped second mate reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId(), null);
            }

            long singletonsCount = SequenceUtil.getLineCount(singletons);
            getPipelineCtx().getLogger().info("total unpaired, unmapped reads: " + (singletonsCount / 4));
            if (singletonsCount == 0)
            {
                singletons.delete();
            }
            else
            {
                output.addSequenceOutput(singletons, "Unmapped singleton reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId(), null);
            }
        }

        return output;
    }

    public static void writeUnmappedReadsAsFastq(File inputBam, File paired1, File paired2, File singletons, Logger log) throws PipelineJobException
    {
        FastqWriterFactory fact = new FastqWriterFactory();
        try (FastqWriter paired1Writer = fact.newWriter(paired1);FastqWriter paired2Writer = fact.newWriter(paired2);FastqWriter singletonsWriter = fact.newWriter(singletons))
        {
            SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
            try (SamReader reader = samReaderFactory.open(inputBam);SamReader mateReader = samReaderFactory.open(inputBam))
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
                                        log.error("Unable to find mate for read: " + r.getReadName());
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
            DNASequence seq = new DNASequence(bases);
            bases = seq.getReverseComplement().getSequenceAsString();

            qualities = StringUtils.reverse(qualities);
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

                            SequenceUtil.writeFastaRecord(writer, name, r.getReadString(), (lineLength == null ? 9999999: lineLength));
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
