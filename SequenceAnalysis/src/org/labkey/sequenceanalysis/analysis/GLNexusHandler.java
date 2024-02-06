package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.BgzipRunner;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler.VCF_CATEGORY;

/**
 * Created by bimber on 2/3/2016.
 */
public class GLNexusHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    protected FileType _gvcfFileType = new FileType(List.of(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public GLNexusHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Run GLNexus", "This will run GLNexus on the selected gVCFs.", null, Arrays.asList(
                ToolParameterDescriptor.create("binVersion", "GLNexus Version", "The version of GLNexus to run, which is passed to their docker container", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, "v1.4.3"),
                ToolParameterDescriptor.create("configType", "Config Type", "This is passed to the --config argument of GLNexus.", "ldk-simplecombo", new JSONObject()
                {{
                    put("multiSelect", false);
                    put("allowBlank", false);
                    put("storeValues", "gatk;DeepVariant;DeepVariantWGS;DeepVariantWES");
                    put("initialValues", "DeepVariant");
                    put("delimiter", ";");
                    put("joinReturnValue", true);
                }}, null),
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, "CombinedGenotypes")
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {

        return o.getFile() != null && _gvcfFileType.isType(o.getFile());
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

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Set<Integer> genomeIds = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
            else if (genomeIds.isEmpty())
            {
                throw new PipelineJobException("No genome ID found for inputs");
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
                action.addInput(so.getFile(), "Input gVCF File");
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }
            else if (genomeIds.isEmpty())
            {
                throw new PipelineJobException("No genome ID found for inputs");
            }

            int genomeId = genomeIds.iterator().next();

            String basename = StringUtils.trimToNull(ctx.getParams().optString("fileBaseName"));
            if (basename == null)
            {
                throw new PipelineJobException("Basename not supplied for output VCF");
            }

            String binVersion = ctx.getParams().optString("binVersion");
            if (binVersion == null)
            {
                throw new PipelineJobException("Missing binVersion");
            }

            String configType = ctx.getParams().optString("configType", "DeepVariant");
            if (configType == null)
            {
                throw new PipelineJobException("Missing configType");
            }

            File outputVcf = new File(ctx.getOutputDir(), basename + ".vcf.gz");

            new GLNexusWrapper(ctx.getLogger()).execute(inputVcfs, outputVcf, ctx.getFileManager(), binVersion, configType);

            ctx.getLogger().debug("adding sequence output: " + outputVcf.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(outputVcf.getName());
            so1.setDescription("GLNexus output.  Version: " + binVersion + ".  Total samples: " + inputFiles.size());
            so1.setFile(outputVcf);
            so1.setLibrary_id(genomeId);
            so1.setCategory(VCF_CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());

            ctx.getFileManager().addSequenceOutput(so1);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }

    public static class GLNexusWrapper extends AbstractCommandWrapper
    {
        public GLNexusWrapper(Logger logger)
        {
            super(logger);
        }

        private File ensureLocalCopy(File input, File workingDirectory, PipelineOutputTracker output) throws PipelineJobException
        {
            try
            {
                if (workingDirectory.equals(input.getParentFile()))
                {
                    return input;
                }

                File local = new File(workingDirectory, input.getName());
                if (!local.exists())
                {
                    getLogger().debug("Copying file locally: " + input.getPath());
                    FileUtils.copyFile(input, local);
                }

                output.addIntermediateFile(local);

                return local;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        public void execute(List<File> inputGvcfs, File outputVcf, PipelineOutputTracker tracker, String binVersion, String configType) throws PipelineJobException
        {
            File workDir = outputVcf.getParentFile();
            tracker.addIntermediateFile(outputVcf);
            tracker.addIntermediateFile(new File(outputVcf.getPath() + ".tbi"));

            List<File> gvcfsLocal = new ArrayList<>();
            for (File f : inputGvcfs)
            {
                gvcfsLocal.add(ensureLocalCopy(f, workDir, tracker));
                ensureLocalCopy(new File(f.getPath() + ".tbi"), workDir, tracker);
            }

            File localBashScript = new File(workDir, "docker.sh");
            tracker.addIntermediateFile(localBashScript);

            try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
            {
                writer.println("#!/bin/bash");
                writer.println("set -x");
                writer.println("WD=`pwd`");
                writer.println("HOME=`echo ~/`");
                writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
                writer.println("sudo $DOCKER pull ghcr.io/dnanexus-rnd/glnexus:" + binVersion);
                writer.println("sudo $DOCKER run --rm=true \\");
                writer.println("\t-v \"${WD}:/work\" \\");
                writer.println("\t-v \"${HOME}:/homeDir\" \\");
                writer.println("\t -w /work \\");
                if (!StringUtils.isEmpty(System.getenv("TMPDIR")))
                {
                    writer.println("\t-v \"${TMPDIR}:/tmp\" \\");
                }
                writer.println("\t-u $UID \\");
                writer.println("\t-e USERID=$UID \\");

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                if (maxRam != null)
                {
                    writer.println("\t--memory='" + maxRam + "g' \\");
                }
                writer.println("\tghcr.io/dnanexus-rnd/glnexus:" + binVersion + " \\");
                writer.println("\tglnexus_cli \\");
                writer.println("\t--config " + configType + " \\");

                writer.println("\t--trim-uncalled-alleles \\");

                if (maxRam != null)
                {
                    writer.println("\t--mem-gbytes " + maxRam + "\\");
                }

                Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
                if (maxThreads != null)
                {
                    writer.println("\t--threads " + maxThreads + " \\");
                }

                gvcfsLocal.forEach(f -> {
                    writer.println("\t/work/" + f.getName() + " \\");
                });

                File bcftools = BcftoolsRunner.getBcfToolsPath();
                File bgzip = BgzipRunner.getExe();
                writer.println("\t| " + bcftools.getPath() + " view | " + bgzip.getPath() + " -c > " + outputVcf.getPath());

                // Command will fail if this exists:
                File dbDir = new File (outputVcf.getParentFile(), "GLnexus.DB");
                tracker.addIntermediateFile(dbDir);
                if (dbDir.exists())
                {
                    getLogger().debug("Deleting pre-existing GLnexus.DB dir");
                    FileUtils.deleteDirectory(dbDir);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            setWorkingDir(workDir);
            execute(Arrays.asList("/bin/bash", localBashScript.getPath()));

            if (!outputVcf.exists())
            {
                throw new PipelineJobException("File not found: " + outputVcf.getPath());
            }

            File idxFile = new File(outputVcf.getPath() + ".tbi");
            if (!idxFile.exists())
            {
                throw new PipelineJobException("Missing index: " + idxFile.getPath());
            }
        }
    }
}