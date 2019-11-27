package org.labkey.sequenceanalysis.run.alignment;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
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
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.IOException;
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

    public static String LONG_READS = "longReads";

    public static class StarAlignmentStep extends AbstractAlignmentPipelineStep<StarWrapper> implements AlignmentStep
    {
        public StarAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new StarWrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public String getAlignmentDescription()
        {
            List<String> lines = new ArrayList<>();
            lines.add("Aligner: " + getProvider().getName());

            Integer gtfId = getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            if (gtfId != null)
            {
                File gtfFile = getPipelineCtx().getSequenceSupport().getCachedData(gtfId);
                if (gtfFile == null)
                {
                    ExpData d = ExperimentService.get().getExpData(gtfId);
                    if (d != null)
                    {
                        gtfFile = d.getFile();
                    }
                }

                if (gtfFile != null)
                {
                    lines.add("GTF/GFF: " + gtfFile.getName());
                }
            }

            return lines.isEmpty() ? null : StringUtils.join(lines, '\n');
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), referenceGenome);
            StarWrapper wrapper = getWrapper();

            getPipelineCtx().getLogger().info("Aligning sample with STAR using two pass mode");
            List<String> args = new ArrayList<>();

            Boolean longReads = getProvider().getParameterByName(LONG_READS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class);
            if (longReads)
            {
                getPipelineCtx().getLogger().info("long reads were selected, using STARlong");
            }

            args.add(wrapper.getExe(longReads).getPath());

            if (!basename.endsWith("."))
            {
                basename = basename + ".";
            }

            args.add("--outSAMtype");
            args.add("BAM");
            args.add("SortedByCoordinate");  //optional

            args.add("--outBAMcompression");
            args.add("6");

            args.add("--outSAMunmapped");
            args.add("None");
            //args.add("KeepPairs");

            args.add("--genomeDir");
            File indexDir = referenceGenome.getAlignerIndexDir(getProvider().getName());
            args.add(indexDir.getPath());

            //TODO: consider option?
            args.add("--twopassMode");
            args.add("Basic");

            args.add("--outFileNamePrefix");
            args.add((new File(outputDirectory, basename)).getPath());

            args.add("--outReadsUnmapped");
            args.add("Fastq");

            //  $RAW_DIR/$line\_R1.fastq.gz $RAW_DIR/$line\_R2.fastq.gz
            args.add("--readFilesIn");
            args.add(inputFastq1.getPath());
            if (inputFastq2 != null)
            {
                args.add(inputFastq2.getPath());
            }

            FileType gz = new FileType(".gz");
            if (gz.isType(inputFastq1))
            {
                args.add("--readFilesCommand");
                args.add("zcat");
            }

            Boolean addSAMStrandField = getProvider().getParameterByName("addSAMStrandField").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
            if (addSAMStrandField)
            {
                args.add("--outSAMstrandField");
                args.add("intronMotif");
            }

            Boolean removeNoncanonicalUnannotated = getProvider().getParameterByName("removeNoncanonicalUnannotated").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
            if (removeNoncanonicalUnannotated)
            {
                args.add("--outFilterIntronMotifs");
                args.add("RemoveNoncanonicalUnannotated");
            }

            args.add("--outSAMattributes");
            args.add("All");

            args.addAll(getClientCommandArgs());

            //GTF
            boolean hasGtf = !StringUtils.isEmpty(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
            if (hasGtf)
            {
                getPipelineCtx().getLogger().debug("using splice sites file");
                File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
                if (gtf.exists())
                {
                    output.addInput(gtf, "Splice Sites File");

                    args.add("--sjdbGTFfile");
                    args.add(gtf.getPath());

                    FileType gtfType = new FileType("gtf");
                    if (!gtfType.isType(gtf))
                    {
                        String parentTranscript = StringUtils.trimToNull(getProvider().getParameterByName("sjdbGTFtagExonParentTranscript").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
                        if (parentTranscript != null)
                        {
                            args.add("--sjdbGTFtagExonParentTranscript");
                            args.add(parentTranscript);
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

                args.add("--quantMode");
                args.add("GeneCounts");
            }

            addThreadArgs(args);
            getWrapper().execute(args);

            //check for output
            File out = new File(outputDirectory, basename + "Aligned.sortedByCoord.out.bam");
            if (!out.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + out.getPath());
            }

            output.addOutput(out, AlignmentOutputImpl.BAM_ROLE);

            if (hasGtf)
            {
                File readCounts = new File(outputDirectory, basename + "ReadsPerGene.out.tab");
                if (!readCounts.exists())
                {
                    throw new PipelineJobException("Unable to find expected output: " + readCounts.getPath());
                }

                File readCountsMoved = new File(outputDirectory, basename + "ReadsPerGene.out.txt");
                try
                {
                    if (readCountsMoved.exists())
                    {
                        readCountsMoved.delete();
                    }
                    FileUtils.moveFile(readCounts, readCountsMoved);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                output.addOutput(readCountsMoved, "Reads Per Gene");
                output.addSequenceOutput(readCountsMoved, rs.getName() + " Gene Counts (STAR)", "Reads Per Gene", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
            }

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            //set permissions on directories.  it is possible we actually want to delete these
            if (SystemUtils.IS_OS_LINUX)
            {
                List<String> dirs = Arrays.asList("_STARgenome", "_STARpass1", "_STARtmp");
                for (String name : dirs)
                {
                    File f = new File(outputDirectory, basename + name);
                    if (f.exists())
                    {
                        try
                        {
                            getPipelineCtx().getLogger().debug("changing permissions on directory: " + f.getPath());
                            recursivelyChangeDirectoryPermissions(f);
                        }
                        catch (IOException e)
                        {
                            throw new PipelineJobException(e);
                        }
                    }
                    else
                    {
                        getPipelineCtx().getLogger().debug("directory not found, skipping: " + f.getPath());
                    }
                }
            }
            else
            {
                getPipelineCtx().getLogger().debug("OS is not linux, no need to change permissions");
            }

            return output;
        }

        private void recursivelyChangeDirectoryPermissions(File f) throws IOException
        {
            if (f.isDirectory())
            {
                Runtime.getRuntime().exec(new String[]{"chmod", "775", f.getPath()});

                File[] children = f.listFiles();
                if (children != null)
                {
                    for (File child : children)
                    {
                        recursivelyChangeDirectoryPermissions(child);
                    }
                }
            }
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
            getPipelineCtx().getLogger().info("Creating STAR index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                List<String> args = new ArrayList<>();
                args.add(getWrapper().getExe(false).getPath());

                args.add("--runMode");
                args.add("genomeGenerate");

                args.add("--genomeDir");
                args.add(indexDir.getPath());

                args.add("--genomeFastaFiles");
                args.add(referenceGenome.getWorkingFastaFile().getPath());

                //NOTE: if the genome has a large number of contigs, this param can be necessary
                try (IndexedFastaSequenceFile idx = new IndexedFastaSequenceFile(referenceGenome.getWorkingFastaFile()))
                {
                    int refNumber = idx.getSequenceDictionary().getSequences().size();
                    long genomeLength = idx.getSequenceDictionary().getReferenceLength();
                    if (refNumber > 500)
                    {
                        getPipelineCtx().getLogger().info("reference has " + refNumber + " contigs, adjusting parameters");
                        getPipelineCtx().getLogger().info("genome length: " + genomeLength);

                        Double scale = Math.min(18.0, (Math.log((genomeLength / refNumber)) / Math.log(2)));
                        args.add("--genomeChrBinNbits");
                        args.add(String.valueOf(scale.intValue()));
                    }

                    //and small genomes may require this
                    Double genomeSAindexNbases = Math.min(14.0, ((Math.log(genomeLength) / Math.log(2)) / 2) - 1);
                    args.add("--genomeSAindexNbases");
                    args.add(String.valueOf(genomeSAindexNbases.intValue()));
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

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
                    put("allowBlank", true);
                }}, null),
                ToolParameterDescriptor.create(LONG_READS, "Reads >500bp", "If the reads are expected to exceed 500bp (per pair), this will use STARlong instead of STAR.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("sjdbGTFtagExonParentTranscript", "Exon Parent Transcript", "This is only required for GFF3 files.  It is the annotation used to assign exons to transcripts.  For GFF3 files this is usually Parent.  It will be ignored if a GTF file is used.", "textfield", null, "Parent"),
                ToolParameterDescriptor.create("addSAMStrandField", "Add SAM Strand Field", "If you have unstranded data and plan to use cufflinks, this should be checked.  It will add the XS tag to the output BAM file.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, true),
                ToolParameterDescriptor.create("removeNoncanonicalUnannotated", "Remove Noncanonical Unannotated Junctions", "If checked, the argument --outFilterIntronMotifs=RemoveNoncanonicalUnannotated will be added, which will filter out noncanonical, unannotated junctions.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, true),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--outFilterMismatchNmax"), "outFilterMismatchNmax", "Max Mismatch", "Alignments with more than this number of mismatches will be filtered", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--outFilterMismatchNoverLmax"), "outFilterMismatchNoverLmax", "Alignment Mismatch Ratio", "An alignment will be output only if its ratio of mismatches to *mapped* length is less than or equal to this value.  Defaults to 0.3", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                    put("maxValue", 1);
                }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--outFilterMismatchNoverReadLmax"), "outFilterMismatchNoverReadLmax", "Read Mismatch Ratio", "An alignment will be output only if its ratio of mismatches to *read* length is less than or equal to this value.  Defaults to 1.0", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--outFilterMultimapNmax"), "outFilterMultimapNmax", "Max Number of Alignments", "Maximum number of loci the read is allowed to map to. Alignments (all of them) will be output only if the read maps to no more loci than this value.", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 10)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "https://github.com/alexdobin/STAR/", true, true, ALIGNMENT_MODE.MERGE_THEN_ALIGN);

            setAlwaysCacheIndex(true);
        }

        public StarAlignmentStep create(PipelineContext context)
        {
            return new StarAlignmentStep(this, context);
        }
    }

    protected File getExe(boolean longReads)
    {
            return SequencePipelineService.get().getExeForPackage("STARPATH", (longReads ? "STARlong" : "STAR"));
    }
}
