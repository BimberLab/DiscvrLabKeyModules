package org.labkey.sequenceanalysis.run.analysis;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PbsvJointCallingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private static final FileType FILE_TYPE = new FileType(".svsig.gz");

    public PbsvJointCallingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "Pbsv Call", "Runs pbsv call, which jointly calls genotypes from PacBio data", null, Arrays.asList(
                ToolParameterDescriptor.create("fileName", "VCF Filename", "The name of the resulting file.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                    put("doNotIncludeInTemplates", true);
                }}, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && FILE_TYPE.isType(o.getFile());
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
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.add("call");

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                args.add("-j");
                args.add(String.valueOf(maxThreads));
            }

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenomes().iterator().next();
            args.add(genome.getWorkingFastaFile().getPath());

            inputFiles.forEach(f -> {
                args.add(f.getFile().getPath());
            });

            String fileName = ctx.getParams().getString("fileName");
            if (!fileName.toLowerCase().endsWith("vcf.gz"))
            {
                fileName = fileName + ".vcf.gz";
            }

            File vcfOut = new File(ctx.getOutputDir(), fileName);
            args.add(vcfOut.getPath());

            new SimpleScriptWrapper(ctx.getLogger()).execute(args);

            if (!vcfOut.exists())
            {
                throw new PipelineJobException("Unable to find file: " + vcfOut.getPath());
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("pbsv call: " + fileName);
            so.setFile(vcfOut);
            so.setCategory("PBSV VCF");
            so.setLibrary_id(genome.getGenomeId());

            ctx.addSequenceOutput(so);
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("PBSVPATH", "pbsv");
        }
    }
}
