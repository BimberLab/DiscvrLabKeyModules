package org.labkey.sequenceanalysis.run.util;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 4/2/2017.
 */
public class GenomicsDBImportHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public static final String NAME = "GenomicsDBImport";
    public static final String CATEGORY = "GenomicsDB Workspace";

    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public GenomicsDBImportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s GenomicsDBImport on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.", null, Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
                ToolParameterDescriptor.create("doCopyLocal", "Copy gVCFs To Working Directory", "If selected, the gVCFs will be copied to the working directory first, which can improve performance when working with a large set of files.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--batch-size"), "batchSize", "Batch Size", "Batch size controls the number of samples for which readers are open at once and therefore provides a way to minimize memory consumption. However, it can take longer to complete. Use the consolidate flag if more than a hundred batches were used. This will improve feature read time. batchSize=0 means no batching (i.e. readers for all samples will be opened at once) Defaults to 0.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--reader-threads"), "readerThreads", "Reader Threads", "How many simultaneous threads to use when opening VCFs in batches; higher values may improve performance when network latency is an issue", "ldk-integerfield", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && _gvcfFileType.isType(o.getFile());
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

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            boolean doCopyLocal = ctx.getParams().optBoolean("doCopyLocal", false);

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
                action.addInput(so.getFile(), "Input gVCF");
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }

            int genomeId = genomeIds.iterator().next();
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            //TODO: consider whether we can do some kind of local resume, based on whether files exist in the workspace
            String basename = ctx.getParams().getString("fileBaseName");
            File outputFile = new File(ctx.getOutputDir(), basename + (basename.endsWith(".") ? "" : ".") + "gdb");

            Set<File> toDelete = new HashSet<>();
            List<File> vcfsToProcess = new ArrayList<>();
            if (doCopyLocal)
            {
                ctx.getLogger().info("making local copies of gVCFs");
                vcfsToProcess.addAll(GenotypeGVCFsWrapper.copyVcfsLocally(inputVcfs, toDelete, null, ctx.getLogger(), false));
            }
            else
            {
                vcfsToProcess.addAll(inputVcfs);
            }

            GenomicsDbImportWrapper wrapper = new GenomicsDbImportWrapper(ctx.getLogger());
            List<String> options = new ArrayList<>(getClientCommandArgs(ctx.getParams()));

            wrapper.execute(genome, vcfsToProcess, outputFile, options);

            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputFile.getPath());
            }

            ctx.getLogger().debug("adding sequence output: " + outputFile.getPath());
            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setName(outputFile.getName());

            int sampleCount = 0;
            for (File gVCF : inputVcfs)
            {
                try (VCFFileReader reader = new VCFFileReader(gVCF))
                {
                    VCFHeader header = reader.getFileHeader();
                    sampleCount += header.getSampleNamesInOrder().size();
                }
            }

            so1.setDescription("GATK GenomicsDB, created from " + inputFiles.size() + " files, " + sampleCount + " samples.  GATK Version: " + wrapper.getVersionString());
            so1.setFile(outputFile);
            so1.setLibrary_id(genomeId);
            so1.setCategory(CATEGORY);
            so1.setContainer(ctx.getJob().getContainerId());
            so1.setCreated(new Date());
            so1.setModified(new Date());
            ctx.addSequenceOutput(so1);
            action.addOutput(outputFile, "GenomicsDB Workspace", false);

            action.setEndTime(new Date());
            ctx.addActions(action);

            if (!toDelete.isEmpty())
            {
                ctx.getLogger().info("deleting locally copied gVCFs");
                for (File f : toDelete)
                {
                    f.delete();
                }
            }
        }
    }
}
