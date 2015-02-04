package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.SystemUtils;
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
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.run.util.BlastNWrapper;
import org.labkey.sequenceanalysis.util.FastqToFastaConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class BlastUnmappedReadAnalysis extends AbstractCommandPipelineStep<BlastNWrapper> implements AnalysisStep
{
    public BlastUnmappedReadAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new BlastNWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<BlastUnmappedReadAnalysis>
    {
        public Provider()
        {
            super("BlastUnmappedReads", "BLAST Unmapped Reads", null, "This will BLAST any unmapped reads against NCBI's nr database, creating a summary report of hits.", Collections.<ToolParameterDescriptor>emptyList(), null, null);
        }

        @Override
        public BlastUnmappedReadAnalysis create(PipelineContext ctx)
        {
            return new BlastUnmappedReadAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        List<File> fastqs = new ArrayList<>();

        File paired1 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_paired1.fastq");
        fastqs.add(paired1);

        File paired2 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_paired2.fastq");
        fastqs.add(paired2);

        File singletons = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + "_singletons.fastq");
        fastqs.add(singletons);

        UnmappedReadExportAnalysis.writeUnmappedReads(inputBam, paired1, paired2, singletons, getPipelineCtx().getLogger());

        for (File f : fastqs)
        {
            output.addIntermediateFile(f);

            getPipelineCtx().getLogger().info("converting to FASTA: " + f.getName());

            output.addInput(f, "Unmapped Reads FASTQ");
            FastqToFastaConverter converter = new FastqToFastaConverter(getPipelineCtx().getLogger());

            File outputFasta = new File(outputDir, FileUtil.getBaseName(f) + ".fasta");
            converter.execute(outputFasta, Arrays.asList(f));
            output.addIntermediateFile(outputFasta);

            File blastResults = new File(outputDir, FileUtil.getBaseName(f) + ".bls");
            getWrapper().doRemoteBlast(outputFasta, blastResults);
            output.addInput(blastResults, "BLAST Results");
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }
}
