package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
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
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
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

    public static class GSnapAlignmentStep extends AbstractAlignmentPipelineStep<GSnapWrapper> implements AlignmentStep
    {
        public GSnapAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new GSnapWrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);
            File inputFastq2 = assertSingleFile(inputFastqs2);

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), referenceGenome);
            GSnapWrapper wrapper = getWrapper();

            List<String> args = new ArrayList<>();
            args.add(wrapper.getGSNapExe().getPath());

            Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                args.add("-t"); //multi-threaded
                args.add(threads.toString());
            }

            FileType gz = new FileType(".gz");
            if (gz.isType(inputFastq1))
            {
                args.add("--gunzip");
            }

            // -d Name of genome (always the provider)
            args.add("-d");
            args.add(getProvider().getName());

            // -D <GSNAP DB dir>
            args.add("-D");
            File indexDir = referenceGenome.getAlignerIndexDir(getProvider().getName());
            args.add(indexDir.getPath());

            args.add("-A"); //SAM output
            args.add("sam");

            args.addAll(getClientCommandArgs());

            args.add("--use-sarray");
            args.add("1");  //use suffix array, which should increase speed

            args.add("--batch");
            args.add("5");

            //look for novel splicing
            args.add("-N");
            args.add("1");

            if (!StringUtils.isEmpty(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx())))
            {
                getPipelineCtx().getLogger().info("creating splice site file");
                File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("splice_sites_file").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
                if (gtf.exists())
                {
                    //cat <gtf file> | gtf_splicesites > foo.splicesites
                    List<String> params = new ArrayList<>();

                    File spliceSitesExe = SequencePipelineService.get().getExeForPackage("GSNAPPATH", "gtf_splicesites");
                    params.add("/bin/sh");
                    params.add("-c");
                    params.add("cat \"" + gtf.getPath() + "\" | \"" + spliceSitesExe.getPath() + "\"");

                    File spliceOutput1 = new File(outputDirectory, FileUtil.getBaseName(gtf) + "_splicesites");
                    if (spliceOutput1.exists())
                    {
                        getPipelineCtx().getLogger().debug("deleting existing file: " + spliceOutput1.getPath());
                        spliceOutput1.delete();
                    }
                    getWrapper().execute(params, spliceOutput1);

                    if (!spliceOutput1.exists())
                    {
                        throw new PipelineJobException("Unable to find splice file: " + spliceOutput1.getPath());
                    }

                    //cat <file> | iit_store -o <splicesitesfile>
                    File spliceOutput2 = new File(outputDirectory, FileUtil.getBaseName(gtf) + "_splice");
                    List<String> params2 = new ArrayList<>();

                    File storeExe = SequencePipelineService.get().getExeForPackage("GSNAPPATH", "iit_store");
                    params2.add("/bin/sh");
                    params2.add("-c");
                    params2.add("cat \"" + spliceOutput1.getPath() + "\" | \"" + storeExe.getPath() + "\" -o \"" + spliceOutput2.getPath() + "\"");
                    getWrapper().execute(params2);

                    spliceOutput2 = new File(spliceOutput2.getPath() + ".iit");
                    if (!spliceOutput2.exists())
                    {
                        throw new PipelineJobException("Unable to find splice file: " + spliceOutput2.getPath());
                    }

                    output.addIntermediateFile(spliceOutput1);
                    //output.addIntermediateFile(spliceOutput2);

                    args.add("-s");
                    args.add(spliceOutput2.getPath());
                }
                else
                {
                    throw new PipelineJobException("expected GTF file does not exist: " + gtf.getPath());
                }
            }

            //--fails-as-input
            if (getProvider().getParameterByName("outputFailed").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
            {
                args.add("--failed-input");
                args.add(new File(outputDirectory, basename).getPath() + "_failed");
            }

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

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
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
            getPipelineCtx().getLogger().info("Creating GSnap/GMap index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = outputDir; //note: GNSAP will create a subdirectory
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
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
                getWrapper().setWorkingDir(indexDir);

                args.add("-d");
                args.add(getProvider().getName());
                args.add(referenceGenome.getWorkingFastaFile().getPath());

                //TODO: conditionalize?
                args.add("-k");
                args.add("12");

                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), new File(indexDir, getProvider().getName()), getProvider().getName(), referenceGenome);

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("GSnap", "GSnap is a splice aware aligner, suitable for RNA-Seq.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("splice_sites_file", "Gene File", "This is the ID of a GTF file containing genes from this genome.  It will be used to identify splice sites.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-m"), "mismatches", "Max Mismatches", "Controls the maximum number of mismatches.  If the value is less than 1, it will be interpreted as a percentage.  Otherwise it will be a fixed cutoff.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 0.05),
                    new ToolParameterDescriptor(CommandLineParam.create("-N"), "allow_novel_splicing", "Allow Novel Splicing", null, "checkbox", true, new JSONObject()
                    {{
                        put("checked", true);
                    }})
                    {
                        @Override
                        public String extractValueForCommandLine(PipelineJob job, PipelineStepProvider provider, int stepIdx) throws PipelineJobException
                        {
                            Boolean ret = extractValue(job, provider, stepIdx, Boolean.class);
                            if (ret == null || !ret)
                            {
                                return "0";
                            }
                            else
                            {
                                return "1";
                            }
                        }
                    },
                    ToolParameterDescriptor.create("outputFailed", "Output Failed", "If checked, the tool will output failed reads to a separate file", "checkbox", new JSONObject()
                    {{
                        put("checked", false);
                    }}, false)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "http://research-pub.gene.com/gmap/", true, true);
        }

        public GSnapAlignmentStep create(PipelineContext context)
        {
            return new GSnapAlignmentStep(this, context);
        }
    }

    protected File getGMapExe()
    {
        return SequencePipelineService.get().getExeForPackage("GSNAPPATH", "gmap");
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
