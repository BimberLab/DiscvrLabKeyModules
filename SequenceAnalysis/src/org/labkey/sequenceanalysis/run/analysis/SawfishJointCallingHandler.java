package org.labkey.sequenceanalysis.run.analysis;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class SawfishJointCallingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private static final String OUTPUT_CATEGORY = "Sawfish VCF";

    public SawfishJointCallingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Sawfish Joint-Call", "Runs sawfish joint-call, which jointly calls SVs from PacBio CCS data", new LinkedHashSet<>(List.of("sequenceanalysis/panel/VariantScatterGatherPanel.js")), Arrays.asList(
                ToolParameterDescriptor.create("fileName", "VCF Filename", "The name of the resulting file.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                    put("doNotIncludeInTemplates", true);
                }}, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && SequenceUtil.FILETYPE.bcf.getFileType().isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<File> filesToProcess = inputFiles.stream().map(SequenceOutputFile::getFile).collect(Collectors.toList());

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenomes().iterator().next();
            String outputBaseName = ctx.getParams().getString("fileName");
            if (!outputBaseName.toLowerCase().endsWith(".gz"))
            {
                outputBaseName = outputBaseName.replaceAll(".gz$", "");
            }

            if (!outputBaseName.toLowerCase().endsWith(".vcf"))
            {
                outputBaseName = outputBaseName.replaceAll(".vcf$", "");
            }

            File expectedFinalOutput = new File(ctx.getOutputDir(), outputBaseName + ".vcf.gz");

            File ouputVcf = runSawfishCall(ctx, filesToProcess, genome, outputBaseName);

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("Sawfish call: " + outputBaseName);
            so.setFile(ouputVcf);
            so.setCategory(OUTPUT_CATEGORY);
            so.setLibrary_id(genome.getGenomeId());

            ctx.addSequenceOutput(so);
        }

        private File runSawfishCall(JobContext ctx, List<File> inputs, ReferenceGenome genome, String outputBaseName) throws PipelineJobException
        {
            if (inputs.isEmpty())
            {
                throw new PipelineJobException("No inputs provided");
            }

            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("joint-call");

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                args.add("--threads");
                args.add(String.valueOf(maxThreads));
            }

            for (File sample : inputs)
            {
                args.add("--sample");
                args.add(sample.getParentFile().getPath());
            }

            File outDir = new File(ctx.getOutputDir(), "sawfish");
            args.add("--output-dir");
            args.add(outDir.getPath());

            new SimpleScriptWrapper(ctx.getLogger()).execute(args);

            File vcfOut = new File(outDir, "genotyped.sv.vcf.gz");
            if (!vcfOut.exists())
            {
                throw new PipelineJobException("Unable to find file: " + vcfOut.getPath());
            }

            File vcfOutFinal = new File(ctx.getOutputDir(), outputBaseName + ".vcf.gz");

            try
            {
                if (vcfOutFinal.exists())
                {
                    vcfOutFinal.delete();
                }
                FileUtils.moveFile(vcfOut, vcfOutFinal);

                File targetIndex = new File(vcfOutFinal.getPath() + ".tbi");
                if (targetIndex.exists())
                {
                    targetIndex.delete();
                }

                File origIndex = new File(vcfOut.getPath() + ".tbi");
                if (origIndex.exists())
                {
                    FileUtils.moveFile(origIndex, targetIndex);
                }
                else
                {
                    SequenceAnalysisService.get().ensureVcfIndex(vcfOutFinal, ctx.getLogger(), true);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return vcfOutFinal;
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("SAWFISHPATH", "sawfish");
        }
    }
}
