package org.labkey.sequenceanalysis.run.preprocessing;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

public class SummarizeAlignmentsStep extends AbstractPipelineStep implements AnalysisStep
{
    public SummarizeAlignmentsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<SummarizeAlignmentsStep>
    {
        public Provider()
        {
            super("SummarizeAlignments", "Summarize Alignments", null, "This will produce a table summarizing unique alignments in this BAM.  It was originally created to summarize genomic insertions.", null, null, null);
        }

        @Override
        public SummarizeAlignmentsStep create(PipelineContext ctx)
        {
            return new SummarizeAlignmentsStep(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File tsv = new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(inputBam.getName()) + ".summary.txt");

        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        fact.referenceSequence(referenceGenome.getWorkingFastaFile());
        try (SamReader bamReader = fact.open(inputBam); SAMRecordIterator it = bamReader.iterator(); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(tsv), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Chr", "Start", "End", "Strand", "ReadLength", "RefLength", "Ratio", "Cigar"});
            NumberFormat pctFormat = NumberFormat.getPercentInstance();
            pctFormat.setMaximumFractionDigits(1);

            while(it.hasNext())
            {
                SAMRecord rec = it.next();
                if (rec.isSecondaryAlignment() || rec.getReadUnmappedFlag())
                {
                    continue;
                }

                Double ratio = Double.valueOf(rec.getLengthOnReference()) / rec.getReadLength();
                String[] vals = new String[]{rec.getContig(), String.valueOf(rec.getReadNegativeStrandFlag() ? rec.getEnd() : rec.getStart()), (rec.getReadNegativeStrandFlag() ? "-" : "+"), String.valueOf(rec.getReadLength()), String.valueOf(rec.getLengthOnReference()), pctFormat.format(ratio), rec.getCigarString()};
                writer.writeNext(vals);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addOutput(tsv, "Alignment Summary Table");

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
