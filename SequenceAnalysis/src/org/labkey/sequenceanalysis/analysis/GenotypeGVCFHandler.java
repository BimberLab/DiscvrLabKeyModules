package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.bed.FullBEDFeature;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/26/2014.
 */
public class GenotypeGVCFHandler implements SequenceOutputHandler
{
    private FileType _vcfFileType = new FileType(Arrays.asList("vcf", "bcf"), "vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public GenotypeGVCFHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Run GATK JointGenotyper";
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public String getButtonJSHandler()
    {
        //TODO
        return "SequenceAnalysis.window.LiftoverWindow.buttonHandler";
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        //TODO
        return new LinkedHashSet<>(Arrays.asList(ClientDependency.fromFilePath("sequenceanalysis/window/LiftoverWindow.js")));
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _vcfFileType.isType(f.getFile());
    }

    @Override
    public void processFiles(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
    {
        for (SequenceOutputFile f : inputFiles)
        {
            job.getLogger().info("processing output: " + f.getFile().getName());

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            boolean isGzip = f.getFile().getPath().toLowerCase().endsWith(".gz");
            int dots = isGzip ? 2 : 1;
            String extension = isGzip ? FileUtil.getExtension(f.getFile().getName().replace(".gz$", "")) : FileUtil.getExtension(f.getFile());
            String baseName = FileUtil.getBaseName(f.getFile(), dots);

            Integer chainRowId = params.getInt("chainRowId");
            Map<String, Object> chainRow = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES), PageFlowUtil.set("chainFile", "genomeId1", "genomeId2")).getObject(chainRowId, Map.class);
            ExpData chainData = ExperimentService.get().getExpData((Integer)chainRow.get("chainFile"));
            if (chainData == null || !chainData.getFile().exists())
            {
                throw new PipelineJobException("Unable to find chain file: " + chainRowId);
            }

            int sourceGenomeId = (Integer)chainRow.get("genomeId1");
            int targetGenomeId = (Integer)chainRow.get("genomeId2");
            double pct = params.containsKey("pct") ? params.getDouble("pct") : 0.95;
            job.getLogger().info("using minimum percent match: " + pct);

            action.addInput(f.getFile(), "Input File");
            action.addInput(chainData.getFile(), "Chain File");

            File outDir = ((FileAnalysisJobSupport)job).getAnalysisDirectory();
            File lifted = new File(outDir, baseName + ".lifted-" + targetGenomeId + "." + extension);
            File unmappedOutput = new File(outDir, baseName + ".unmapped-" + targetGenomeId + "." + extension);


            action.addOutput(lifted, "Lifted Features", lifted.exists());
            if (lifted.exists())
            {
                SequenceOutputFile so1 = new SequenceOutputFile();
                so1.setName(f.getName() + " (lifted)");
                so1.setDescription("Contains features from " + f.getName() + " after liftover");
                ExpData liftedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                liftedData.setDataFileURI(lifted.toURI());
                liftedData.setName(lifted.getName());
                liftedData.save(job.getUser());

                so1.setDataId(liftedData.getRowId());
                so1.setLibrary_id(targetGenomeId);
                so1.setReadset(f.getReadset());
                so1.setAnalysis_id(f.getAnalysis_id());
                so1.setCategory(f.getCategory());
                so1.setContainer(job.getContainerId());
                so1.setCreated(new Date());
                so1.setCreatedby(job.getUser().getUserId());
                so1.setModified(new Date());
                so1.setModifiedby(job.getUser().getUserId());

                outputsToCreate.add(so1);
            }

            if (!unmappedOutput.exists())
            {
                job.getLogger().info("no unmapped intervals");
            }
            else if (SequenceUtil.getLineCount(unmappedOutput) == 0)
            {
                job.getLogger().info("no unmapped intervals");
                unmappedOutput.delete();
            }
            else
            {
                action.addOutput(unmappedOutput, "Unmapped features", false);

                SequenceOutputFile so2 = new SequenceOutputFile();
                so2.setName(f.getName() + " (lifted/unmapped)");
                so2.setDescription("Contains the unmapped features after attempted liftover of " + f.getName());

                ExpData unmappedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                unmappedData.setName(unmappedOutput.getName());
                unmappedData.setDataFileURI(unmappedOutput.toURI());
                unmappedData.save(job.getUser());

                so2.setDataId(unmappedData.getRowId());
                so2.setLibrary_id(f.getLibrary_id());
                so2.setReadset(f.getReadset());
                so2.setAnalysis_id(f.getAnalysis_id());
                so2.setCategory(f.getCategory());
                so2.setContainer(job.getContainerId());
                so2.setCreated(new Date());
                so2.setCreatedby(job.getUser().getUserId());
                so2.setModified(new Date());
                so2.setModifiedby(job.getUser().getUserId());

                outputsToCreate.add(so2);
            }

            action.setEndTime(new Date());
            actions.add(action);
        }
    }
}
