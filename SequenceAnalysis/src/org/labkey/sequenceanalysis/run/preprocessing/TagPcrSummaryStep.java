package org.labkey.sequenceanalysis.run.preprocessing;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
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
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TagPcrSummaryStep extends AbstractPipelineStep implements AnalysisStep
{
    public TagPcrSummaryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static final String MIN_ALIGNMENTS = "minAlignments";
    public static class Provider extends AbstractAnalysisStepProvider<TagPcrSummaryStep>
    {
        public Provider()
        {
            super("Tag-PCR", "Tag-PCR Integration Sites", null, "This will produce a table summarizing unique alignments in this BAM.  It was originally created to summarize genomic insertions.", Arrays.asList(
                    ToolParameterDescriptor.create(MIN_ALIGNMENTS, "Min Alignments", "The minimum number of alignments to export a position", "ldk-integerfield", null, 2)
            ), null, null);
        }

        @Override
        public TagPcrSummaryStep create(PipelineContext ctx)
        {
            return new TagPcrSummaryStep(this, ctx);
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
        long numRecords = 0L;
        Map<String, Long> alignMap = new HashMap<>();
        ReferenceSequenceFileFactory refFact = new ReferenceSequenceFileFactory();
        try (SamReader bamReader = fact.open(inputBam); SAMRecordIterator it = bamReader.iterator(); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(tsv), '\t', CSVWriter.NO_QUOTE_CHARACTER);ReferenceSequenceFile refSeq = refFact.getReferenceSequenceFile(referenceGenome.getWorkingFastaFile()))
        {
            while (it.hasNext())
            {
                SAMRecord rec = it.next();
                if (rec.isSecondaryAlignment() || rec.getReadUnmappedFlag())
                {
                    continue;
                }

                boolean isFirstMate = !(rec.getProperPairFlag() && rec.getSecondOfPairFlag());
                if (!isFirstMate)
                {
                    continue;
                }

                numRecords++;
                String key = getAlignmentKey(rec);
                long val = alignMap.getOrDefault(key, 0L);
                val++;

                alignMap.put(key, val);
            }

            writer.writeNext(new String[]{"Chr", "Position", "Strand", "Total", "RegionAfterSite", "RegionBeforeSite"});
            Integer minAlignments = getProvider().getParameterByName(MIN_ALIGNMENTS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 2);
            Map<String, ReferenceSequence> refMap = new HashMap<>();
            for (String key : alignMap.keySet())
            {
                long val = alignMap.get(key);
                if (val > minAlignments)
                {
                    String[] vals = key.split("<>");
                    int start = Integer.valueOf(vals[1]);

                    ReferenceSequence ref = refMap.get(vals[0]);
                    if (ref == null)
                    {
                        ref = refSeq.getSequence(vals[0]);
                        refMap.put(vals[0], ref);
                    }

                    String after = ref.getBaseString().substring(start, start + 1000);
                    String before = ref.getBaseString().substring(start - 1000, start);
                    writer.writeNext(new String[]{vals[0], vals[1], vals[2], String.valueOf(val), after, before});
                }
                else
                {
                    getPipelineCtx().getLogger().info("Skipping position: " + key + ", " + val);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addOutput(tsv, "Tag-PCR Integration Sites");
        output.addSequenceOutput(tsv, "Putative Integration Sites: " + rs.getName(), "Tag-PCR Integration Sites", rs.getReadsetId(), null, referenceGenome.getGenomeId(), "Records: " + numRecords);

        return output;
    }

    private String getAlignmentKey(SAMRecord rec)
    {
        int start = (rec.getReadNegativeStrandFlag() ? rec.getEnd() : rec.getStart());
        return rec.getContig() + "<>" + start + "<>" + (rec.getReadNegativeStrandFlag() ? "-" : "+");
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
