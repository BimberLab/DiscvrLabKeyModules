package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), referenceGenome);
            StarWrapper wrapper = getWrapper();

            getPipelineCtx().getLogger().info("Aligning sample with STAR using two pass mode");
            List<String> args1 = new ArrayList<>();
            args1.add(wrapper.getExe().getPath());

            if (!basename.endsWith("."))
            {
                basename = basename + ".";
            }

            args1.add("--outSAMtype");
            args1.add("BAM");
            args1.add("SortedByCoordinate");  //optional

            //args1.add("--outSAMunmapped");

            args1.add("--genomeDir");
            File indexDir = new File(referenceGenome.getWorkingFastaFile().getParentFile(), getProvider().getName());
            args1.add(indexDir.getPath());

            args1.add("--twopassMode");
            args1.add("Basic");

            args1.add("--outFileNamePrefix");
            args1.add((new File(outputDirectory, basename)).getPath());

            args1.add("--outReadsUnmapped");
            args1.add("Fastq");

            //  $RAW_DIR/$line\_R1.fastq.gz $RAW_DIR/$line\_R2.fastq.gz
            args1.add("--readFilesIn");
            args1.add(inputFastq1.getPath());
            if (inputFastq2 != null)
            {
                args1.add(inputFastq2.getPath());
            }

            FileType gz = new FileType(".gz");
            if (gz.isType(inputFastq1))
            {
                args1.add("--readFilesCommand");
                args1.add("zcat");
            }

            Boolean stranded = getProvider().getParameterByName("stranded").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false);
            if (stranded)
            {
                args1.add("--outSAMstrandField");
                args1.add("intronMotif");

                args1.add("--outFilterIntronMotifs");
                args1.add("RemoveNoncanonicalUnannotated");
            }

            //GTF
            if (!StringUtils.isEmpty(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider())))
            {
                getPipelineCtx().getLogger().debug("using splice sites file");
                File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), Integer.class));
                if (gtf.exists())
                {
                    args1.add("--sjdbGTFfile");
                    args1.add(gtf.getPath());

                    FileType gtfType = new FileType("gtf");
                    if (!gtfType.isType(gtf))
                    {
                        String parentTranscript = StringUtils.trimToNull(getProvider().getParameterByName("sjdbGTFtagExonParentTranscript").extractValue(getPipelineCtx().getJob(), getProvider(), String.class));
                        if (parentTranscript != null)
                        {
                            args1.add("sjdbGTFtagExonParentTranscript");
                            args1.add(parentTranscript);
                        }
                        else
                        {
                            throw new PipelineJobException("When selecting a GFF file, you must provide the ID of the annotation that provides parent/child information");
                        }
                    }

                }
                else
                {
                    getPipelineCtx().getLogger().error("Unable to find GTF/GFF file: " + gtf.getPath());
                }
            }

            args1.add("--quantMode");
            args1.add("GeneCounts");

            addThreadArgs(args1);
            getWrapper().execute(args1);

            //check for output
            File out = new File(outputDirectory, basename + "Aligned.sortedByCoord.out.bam");
            if (!out.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + out.getPath());
            }

            output.addOutput(out, AlignmentOutputImpl.BAM_ROLE);

            File readCounts = new File(outputDirectory, basename + "ReadsPerGene.out.tab");
            output.addOutput(readCounts, "Reads Per Gene");

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

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
        public boolean doAddReadGroups()
        {
            return true;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return false;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating STAR index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getProvider().getName(), referenceGenome);
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
                args.add(indexDir.getPath());

                args.add("--genomeFastaFiles");
                args.add(referenceGenome.getWorkingFastaFile().getPath());

                addThreadArgs(args);
                getWrapper().setWorkingDir(indexDir);

                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), referenceGenome);

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("STAR", "STAR is a splice aware aligner, suitable for RNA-Seq.", Arrays.asList(
                ToolParameterDescriptor.createExpDataParam("splice_sites_file", "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.  It will be used to identify splice sites.  If a GFF3 file is selected, you must also provide the ID used to specify parent features.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                    put("extensions", Arrays.asList("gtf", "gff"));
                    put("width", 400);
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("sjdbGTFtagExonParentTranscript", "Exon Parent Transcript", "This is only required for GFF3 files.  It is the annotation used to assign exons to transcripts.  For GFF3 files this is usually Parent.  It will be ignored if a GTF file is used.", "textfield", null, "Parent"),
                ToolParameterDescriptor.create("stranded", "Data Are Stranded?", "If checked, the following arguments will be added: --outSAMstrandField=intronMotif and --outFilterIntronMotifs=RemoveNoncanonicalUnannotated.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
            ), null, "https://github.com/alexdobin/STAR/", true, true);
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
