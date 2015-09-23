package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.GenotypeGVCFsWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 8/26/2014.
 */
public class GenotypeGVCFHandler extends AbstractParameterizedOutputHandler
{
    private FileType _gvcfFileType = new FileType(Arrays.asList(".g.vcf"), ".g.vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public GenotypeGVCFHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "GATK Genotype GVCFs", "This will run GATK\'s GenotypeGVCF on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.  ", null, Arrays.asList(
                ToolParameterDescriptor.create("fileBaseName", "Filename", "This is the basename that will be used for the output gzipped VCF", "textfield", null, "CombinedGenotypes"),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_call_conf"), "stand_call_conf", "Threshold For Calling Variants", "The minimum phred-scaled confidence threshold at which variants should be called", "ldk-numberfield", null, 20),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-stand_emit_conf"), "stand_emit_conf", "Threshold For Emitting Variants", "The minimum phred-scaled confidence threshold at which variants should be emitted (and filtered with LowQual if less than the calling threshold)", "ldk-numberfield", null, 20)
        ));
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _gvcfFileType.isType(f.getFile());
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            GenotypeGVCFsWrapper wrapper = new GenotypeGVCFsWrapper(job.getLogger());

            Set<Integer> genomeIds = new HashSet<>();
            List<File> inputVcfs = new ArrayList<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
                inputVcfs.add(so.getFile());
            }

            if (genomeIds.size() > 1)
            {
                throw new PipelineJobException("The selected files use more than one genome");
            }

            int genomeId = genomeIds.iterator().next();
            ReferenceGenome genome = support.getCachedGenome(genomeId);
            if (genome == null)
            {
                throw new PipelineJobException("Unable to find cached genome for Id: " + genomeId);
            }

            File outDir = ((FileAnalysisJobSupport) job).getAnalysisDirectory();
            String basename = params.get("fileBaseName") != null ? params.getString("fileBaseName") : "CombinedGenotypes";
            basename.replaceAll(".vcf.gz$", "");
            basename.replaceAll(".vcf$", "");

            File outputVcf = new File(outDir, basename + ".vcf.gz");
            List<String> toolParams = new ArrayList<>();
            if (params.get("stand_call_conf") != null)
            {
                toolParams.add("-stand_call_conf");
                toolParams.add(params.get("stand_call_conf").toString());
            }

            if (params.get("stand_emit_conf") != null)
            {
                toolParams.add("-stand_emit_conf");
                toolParams.add(params.get("stand_emit_conf").toString());
            }

            wrapper.execute(genome.getSourceFastaFile(), outputVcf, toolParams, inputVcfs.toArray(new File[inputVcfs.size()]));
            action.addOutput(outputVcf, "Combined VCF", outputVcf.exists(), true);

            if (outputVcf.exists())
            {
                SequenceOutputFile so1 = new SequenceOutputFile();
                so1.setName(outputVcf.getName());
                so1.setDescription("GATK GenotypeGVCF output");
                so1.setFile(outputVcf);
                so1.setLibrary_id(genomeId);
                so1.setCategory("Combined VCF");
                so1.setContainer(job.getContainerId());
                so1.setCreated(new Date());
                so1.setModified(new Date());

                outputsToCreate.add(so1);
            }

            action.setEndTime(new Date());
            actions.add(action);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
