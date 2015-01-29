package org.labkey.sequenceanalysis.run.analysis;

import com.drew.lang.annotations.Nullable;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.lang3.StringUtils;
import org.biojava3.core.sequence.DNASequence;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAnalysisStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
            super("UnalignedReadExport", "Export Unmapped Reads", null, "This will export unmapped reads from each BAM to create FASTQ files.", Collections.<ToolParameterDescriptor>emptyList(), null, null);
        }

        @Override
        public UnmappedReadExportAnalysis create(PipelineContext ctx)
        {
            return new UnmappedReadExportAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome) throws PipelineJobException
    {
        return null;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File paired1 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_paired1.fastq");
        File paired2 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_paired2.fastq");
        File singletons = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_singletons.fastq");

        FastqWriterFactory fact = new FastqWriterFactory();
        try (FastqWriter paired1Writer = fact.newWriter(paired1);FastqWriter paired2Writer = fact.newWriter(paired2);FastqWriter singletonsWriter = fact.newWriter(singletons))
        {
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
                            //pairs where both unmapped
                            if (r.getReadPairedFlag() && r.getMateUnmappedFlag())
                            {
                                if (r.getFirstOfPairFlag())
                                {
                                    SAMRecord mate = reader.queryMate(r);
                                    if (mate != null)
                                    {
                                        FastqRecord fq = samReadToFastqRecord(r, "/1");
                                        paired1Writer.write(fq);

                                        FastqRecord fq2 = samReadToFastqRecord(mate, "/2");
                                        paired2Writer.write(fq2);
                                    }
                                    else
                                    {
                                        getPipelineCtx().getLogger().error("Unable to find mate for read: " + r.getReadName());
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

            if (SequenceUtil.getLineCount(paired1) == 0)
            {
                paired1.delete();
            }
            else
            {
                output.addSequenceOutput(paired1, "Unmapped first mate reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId());
            }

            if (SequenceUtil.getLineCount(paired2) == 0)
            {
                paired2.delete();
            }
            else
            {
                output.addSequenceOutput(paired1, "Unmapped second mate reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId());
            }

            if (SequenceUtil.getLineCount(singletons) == 0)
            {
                singletons.delete();
            }
            else
            {
                output.addSequenceOutput(paired1, "Unmapped singleton reads: " + inputBam.getName(), "Unmapped Reads", model.getReadset(), model.getAnalysisId(), model.getLibraryId());
            }
        }

        return output;
    }

    @Override
    public void performAnalysisOnAll(List<AnalysisModel> analysisModels) throws PipelineJobException
    {

    }

    private FastqRecord samReadToFastqRecord(SAMRecord read, @Nullable String readNameSuffix)
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
}
