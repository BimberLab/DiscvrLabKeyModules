package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 1:01 PM
 */
public class StarWrapper extends AbstractCommandWrapper
{
    public StarWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class StarAlignmentStep extends AbstractCommandPipelineStep<StarWrapper> implements AlignmentStep
    {
        public StarAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new StarWrapper(ctx.getLogger()));
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName());
            StarWrapper wrapper = getWrapper();

            getPipelineCtx().getLogger().info("Aligning sample with STAR... 1st pass");
            List<String> args1 = new ArrayList<>();
            args1.add(wrapper.getExe().getPath());

            args1.add("--genomeDir");
            File indexDir = new File(referenceGenome.getWorkingFastaFile().getParentFile(), getProvider().getName());
            args1.add(indexDir.getPath());

            //only applies if input if gzip
            // --readFilesCommand zcat

            // --outFileNamePrefix $SAMP_DIR\_

            //  $RAW_DIR/$line\_R1.fastq.gz $RAW_DIR/$line\_R2.fastq.gz
            args1.add("--readFilesIn");
            args1.add(inputFastq1.getPath());
            if (inputFastq2 != null)
            {
                args1.add(inputFastq2.getPath());
            }

            addThreadArgs(args1);
            getWrapper().execute(args1);

            //TODO: check for output

            getPipelineCtx().getLogger().info("Creating new index based off of found splice junctions");
            //STAR
            // --runMode genomeGenerate
            // --genomeDir $STAR_GEN_1
            // --genomeFastaFiles $REF_FASTA
            // --sjdbFileChrStartEnd $SAMP_DIR\_SJ.out.tab
            // --sjdbOverhang 75
            // --runThreadN $THREADS

            getPipelineCtx().getLogger().info("Running final STAR alignment");
            //STAR
            // --genomeDir $STAR_GEN_1
            // --readFilesCommand zcat
            // --outFileNamePrefix $SAMP_DIR\_
            // --readFilesIn $RAW_DIR/$line\_R1.fastq.gz $RAW_DIR/$line\_R2.fastq.gz
            // --runThreadN $THREADS


            //make sure SAM exists
            File sam = new File(outputDirectory, basename + ".sam");
            if (!sam.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + sam.getPath());
            }

            //convert to BAM
            File bam = new File(outputDirectory, basename + ".bam");
            bam = new SamFormatConverterWrapper(getPipelineCtx().getLogger()).execute(sam, bam, true);
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + bam.getPath());
            }
            else
            {
                getPipelineCtx().getLogger().info("deleting intermediate SAM file");
                sam.delete();
            }

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);

            return output;
        }

        private void addThreadArgs(List<String> args)
        {
            Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                args.add("--runThreadN"); //multi-threaded
                args.add(threads.toString());
            }
        }

        @Override
        public boolean doMergeUnalignedReads()
        {
            return true;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating STAR index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getProvider().getName());
            if (!hasCachedIndex)
            {
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                List<String> args = new ArrayList<>();
                args.add(getWrapper().getExe().getPath());

                args.add("--runMode");
                args.add("genomeGenerate");

                args.add("--genomeDir");
                args.add(outputDir.getPath());

                args.add("--genomeFastaFiles");
                args.add(referenceGenome.getWorkingFastaFile().getPath());

                addThreadArgs(args);

                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), output);

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("STAR", "STAR is a splice aware aligner, suitable for RNA-Seq.", null, null, "https://github.com/alexdobin/STAR/", true);
        }

        public StarAlignmentStep create(PipelineContext context)
        {
            return new StarAlignmentStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("STARPATH", "STAR");
    }
}
