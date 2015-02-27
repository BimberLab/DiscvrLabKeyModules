package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 1:01 PM
 */
public class GSnapWrapper extends AbstractCommandWrapper
{
    public GSnapWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class GSnapAlignmentStep extends AbstractCommandPipelineStep<GSnapWrapper> implements AlignmentStep
    {
        public GSnapAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new GSnapWrapper(ctx.getLogger()));
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName());
            GSnapWrapper wrapper = getWrapper();

            List<String> args = new ArrayList<>();
            args.add(wrapper.getGSNapExe().getPath());

            Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                args.add("-t"); //multi-threaded
                args.add(threads.toString());
            }

            // -d Name of genome (always the provider)
            args.add("-d");
            args.add(getProvider().getName());

            // -D <GSNAP DB dir>
            args.add("-D");
            File indexDir = new File(referenceGenome.getWorkingFastaFile().getParentFile(), getProvider().getName());
            args.add(indexDir.getPath());

            args.add("-A"); //SAM output
            args.add("sam");

            args.addAll(getClientCommandArgs());

            args.add("--use-sarray");
            args.add("1");  //use suffix array, which should increase speed

            args.add("--batch");
            args.add("4");

            //look for novel splicing
            args.add("-N");
            args.add("1");

            //TODO
            //args.add("-s");
            //args.add(<splice sites file>);
            //cat <gtf file> | gtf_splicesites > foo.splicesites
            //cat <gtf file> | gtf_introns > foo.introns

            // -m <mismatches>

            ///TODO
            //--fails-as-input?
            //args.add("--failed-input");

            args.add("--split-output");
            args.add(new File(outputDirectory, basename).getPath());

            // <file list>
            args.add(inputFastq1.getPath());
            if (inputFastq2 != null)
            {
                args.add(inputFastq2.getPath());
            }

            getWrapper().execute(args);

            //make sure outputs exist, rename to .SAM, then convert
            String[] outputNames = new String[]{"nomapping","halfmapping_uniq","halfmapping_mult","unpaired_uniq","unpaired_mult","paired_uniq","paired_mult","concordant_uniq","concordant_mult","concordant_transloc","halfmapping_transloc","paired_uniq_inv","paired_uniq_long","paired_uniq_scr","unpaired_transloc"};
            for (String name : outputNames)
            {
                File outputFile = new File(outputDirectory, basename + "." + name);
                if (!outputFile.exists())
                {
                    getPipelineCtx().getLogger().debug("Did not find potential output: " + outputFile.getPath());
                    continue;
                }

                if (!SequenceUtil.hasLineCount(outputFile))
                {
                    getPipelineCtx().getLogger().debug("deleting empty output file: " + outputFile.getPath());
                    outputFile.delete();
                    continue;
                }

                getPipelineCtx().getLogger().info("Converting to BAM: " + outputFile.getPath());

                //convert to BAM
                File bam = new File(outputFile.getPath() + ".bam");
                bam = new SamFormatConverterWrapper(getPipelineCtx().getLogger()).execute(outputFile, bam, true);
                if (!bam.exists())
                {
                    throw new PipelineJobException("Unable to find output file: " + bam.getPath());
                }
                else
                {
                    getPipelineCtx().getLogger().info("deleting intermediate SAM file");
                    outputFile.delete();
                }

                if (inputFastq2 == null && "unpaired_uniq".equals(name))
                {
                    output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
                }
                else if (inputFastq2 != null && "concordant_uniq".equals(name))
                {
                    output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
                }
                else
                {
                    output.addOutput(bam, AlignmentOutputImpl.ALIGNMENT_OUTPUT_ROLE);
                }
            }

            return output;
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
            getPipelineCtx().getLogger().info("Creating GSnap/GMap index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = outputDir; //note: GNSAP will create a subdirectory
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getProvider().getName());
            if (!hasCachedIndex)
            {
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                List<String> args = new ArrayList<>();
                File buildExe = getWrapper().getBuildExe();
                args.add(buildExe.getPath());

                // the /bin dir
                args.add("-B");
                args.add(buildExe.getParent());

                args.add("-D");
                args.add(indexDir.getPath());
                args.add("-d");
                args.add(getProvider().getName());
                args.add(referenceGenome.getWorkingFastaFile().getPath());

                //TODO: conditionalize
                args.add("-k");
                args.add("12");

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
            super("GSnap", "GSnap is a splice aware aligner, suitable for RNA-Seq.", Arrays.asList(
                    ToolParameterDescriptor.create("splice_sites_files", "Gene File", "This is the ID of a GTF file containing genes from this genome.  It will be used to identify splice sites.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extension", "gtf");
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-m"), "mismatches", "Max Mismatches", null, "ldk-integerfield", null, 5),
                    new ToolParameterDescriptor(CommandLineParam.create("-N"), "allow_novel_splicing", "Allow Novel Splicing", null, "checkbox", true, new JSONObject()
                    {{
                        put("checked", true);
                    }})
                    {
                        @Override
                        public String extractValueForCommandLine(PipelineJob job, PipelineStepProvider provider) throws PipelineJobException
                        {
                            Boolean ret = extractValue(job, provider, Boolean.class);
                            if (ret == null || !ret)
                            {
                                return "0";
                            }
                            else
                            {
                                return "1";
                            }
                        }
                    }
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "http://research-pub.gene.com/gmap/", true);
        }

        public GSnapAlignmentStep create(PipelineContext context)
        {
            return new GSnapAlignmentStep(this, context);
        }
    }

    protected File getGMapExe()
    {
        return SequencePipelineService.get().getExeForPackage("GSNAPPATH", "gsnap");
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("GSNAPPATH", "gmap_build");
    }

    protected File getGSNapExe()
    {
        return SequencePipelineService.get().getExeForPackage("GSNAPPATH", "gsnap");
    }
}
