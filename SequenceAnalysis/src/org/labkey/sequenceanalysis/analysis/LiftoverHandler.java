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
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.run.util.LiftoverVcfWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class LiftoverHandler implements SequenceOutputHandler
{
    private FileType _bedFileType = new FileType(".bed", false);
    //private FileType _gffFileType = new FileType("gff", false);
    private FileType _vcfFileType = new FileType(Arrays.asList(".vcf", ".bcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public LiftoverHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Lift To Alternate Genome";
    }

    @Override
    public String getDescription()
    {
        return "This will translate (liftover) VCF or BED files from one reference genome to another.  This is often useful to compare data aligned against different genome builds, or to compare between closely related species.";
    }

    @Override
    public String getButtonJSHandler()
    {
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
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList("sequenceanalysis/window/LiftoverWindow.js"));
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
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
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (
                _bedFileType.isType(f.getFile()) ||
                _vcfFileType.isType(f.getFile()));
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Integer chainFileId = params.getInt("chainFileId");
            ExpData chainData = ExperimentService.get().getExpData(chainFileId);
            if (chainData == null || !chainData.getFile().exists())
            {
                throw new PipelineJobException("Unable to find chain file: " + chainFileId);
            }

            support.cacheExpData(chainData);

            int targetGenomeId = params.getInt("targetGenomeId");
            ReferenceGenome targetGenome = SequenceAnalysisService.get().getReferenceGenome(targetGenomeId, job.getUser());
            support.cacheGenome(targetGenome);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {


        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            Integer chainFileId = params.getInt("chainFileId");
            File chainFile = ctx.getSequenceSupport().getCachedData(chainFileId);
            int targetGenomeId = params.getInt("targetGenomeId");

            for (SequenceOutputFile f : inputFiles)
            {
                job.getLogger().info("processing output: " + f.getFile().getName());

                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());

                boolean isGzip = f.getFile().getPath().toLowerCase().endsWith("gz");
                int dots = isGzip ? 2 : 1;
                String baseName = FileUtil.getBaseName(f.getFile(), dots);

                double pct = params.containsKey("pct") ? params.getDouble("pct") : 0.95;
                job.getLogger().info("using minimum percent match: " + pct);

                action.addInput(f.getFile(), "Input File");
                action.addInput(chainFile, "Chain File");

                File outDir = ((FileAnalysisJobSupport) job).getAnalysisDirectory();
                String ext = null;
                if (_bedFileType.isType(f.getFile()))
                {
                    ext = ".bed";
                }
                else if (_vcfFileType.isType(f.getFile()))
                {
                    ext = ".vcf.gz";
                }
                else
                {
                    throw new UnsupportedOperationException("Unsupported file type: " + f.getFile().getName());
                }

                File lifted = new File(outDir, baseName + ".lifted-" + targetGenomeId + ext);
                File unmappedOutput = new File(outDir, baseName + ".unmapped-" + targetGenomeId + ext);

                try
                {
                    if (_bedFileType.isType(f.getFile()))
                    {
                        liftOverBed(chainFile, f.getFile(), lifted, unmappedOutput, job, pct);
                    }
                    else if (_vcfFileType.isType(f.getFile()))
                    {
                        ReferenceGenome targetGenome = ctx.getSequenceSupport().getCachedGenome(targetGenomeId);
                        liftOverVcf(targetGenome, chainFile, f.getFile(), lifted, unmappedOutput, job, pct);
                    }
                }
                catch (Exception e)
                {
                    throw new PipelineJobException(e);
                }

                job.getLogger().info("adding outputs");
                action.addOutput(lifted, "Lifted Features", lifted.exists(), true);
                if (lifted.exists())
                {
                    job.getLogger().info("adding lifted features: " + lifted.getName());

                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setName(f.getName() + " (lifted)");
                    so1.setDescription("Contains features from " + f.getName() + " after liftover");
                    //ExpData liftedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                    //liftedData.setDataFileURI(lifted.toURI());
                    //liftedData.setName(lifted.getName());
                    //liftedData.save(job.getUser());
                    //so1.setDataId(liftedData.getRowId());
                    so1.setFile(lifted);
                    so1.setLibrary_id(targetGenomeId);
                    so1.setReadset(f.getReadset());
                    so1.setAnalysis_id(f.getAnalysis_id());
                    so1.setCategory(f.getCategory());
                    so1.setContainer(job.getContainerId());
                    so1.setCreated(new Date());
                    so1.setModified(new Date());

                    ctx.addSequenceOutput(so1);
                }

                if (!unmappedOutput.exists())
                {
                    job.getLogger().info("no unmapped intervals");
                }
                else if (!SequenceUtil.hasLineCount(unmappedOutput))
                {
                    job.getLogger().info("no unmapped intervals");
                    unmappedOutput.delete();
                }
                else
                {
                    job.getLogger().info("adding unmapped features: " + unmappedOutput.getName());

                    action.addOutput(unmappedOutput, "Unmapped features", false, true);

                    SequenceOutputFile so2 = new SequenceOutputFile();
                    so2.setName(f.getName() + " (lifted/unmapped)");
                    so2.setDescription("Contains the unmapped features after attempted liftover of " + f.getName());

                    //ExpData unmappedData = ExperimentService.get().createData(job.getContainer(), new DataType("Liftover Output"));
                    //unmappedData.setName(unmappedOutput.getName());
                    //unmappedData.setDataFileURI(unmappedOutput.toURI());
                    //unmappedData.save(job.getUser());
                    //so2.setDataId(unmappedData.getRowId());
                    so2.setFile(unmappedOutput);
                    so2.setLibrary_id(f.getLibrary_id());
                    so2.setReadset(f.getReadset());
                    so2.setAnalysis_id(f.getAnalysis_id());
                    so2.setCategory(f.getCategory());
                    so2.setContainer(job.getContainerId());
                    so2.setCreated(new Date());
                    so2.setModified(new Date());

                    ctx.addSequenceOutput(so2);
                }

                action.setEndTime(new Date());
                ctx.addActions(action);
            }
        }
    }

    public void liftOverVcf(ReferenceGenome targetGenome, File chain, File input, File output, File unmappedOutput, PipelineJob job, double pct) throws IOException, PipelineJobException
    {
        LiftoverVcfWrapper wrapper = new LiftoverVcfWrapper(job.getLogger());
        wrapper.doLiftover(input, chain, targetGenome.getWorkingFastaFile(), unmappedOutput, output, pct);

        if (output.exists())
        {
            job.getLogger().info("total variants: " + ProcessVariantsHandler.getVCFLineCount(output, job.getLogger(), false));
            job.getLogger().info("passing variants: " + ProcessVariantsHandler.getVCFLineCount(output, job.getLogger(), true));
        }
    }

    public void liftOverBed(File chain, File input, File output, File unmappedOutput, PipelineJob job, double pct) throws IOException, PipelineJobException
    {
        LiftOver lo = new LiftOver(chain);
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter unmappedWriter = new CSVWriter(PrintWriters.getPrintWriter(unmappedOutput), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            try (FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(input.getAbsolutePath(), new BEDCodec(), false))
            {
                try (CloseableTribbleIterator<BEDFeature> i = reader.iterator())
                {
                    int mapped = 0;
                    int unmapped = 0;

                    while (i.hasNext())
                    {
                        FullBEDFeature f = (FullBEDFeature)i.next();

                        Interval iv = new Interval(f.getContig(), f.getStart(), f.getEnd(), f.getStrand() == Strand.POSITIVE, StringUtils.isEmpty(f.getName()) ? null : f.getName());
                        Interval lifted = lo.liftOver(iv, pct);
                        String score = ((Float)f.getScore()).isNaN() ? "0" : String.valueOf(f.getScore());
                        if (lifted != null)
                        {
                            writer.writeNext(new String[]{lifted.getContig(), String.valueOf(lifted.getStart() - 1), String.valueOf(lifted.getEnd()), f.getName(), score, (lifted.isNegativeStrand() ? "-" : lifted.isPositiveStrand() ? "+" : null), ("Source: " + f.getContig() + ":" + f.getStart() + "-" + f.getEnd())});
                            mapped++;
                        }
                        else
                        {
                            unmappedWriter.writeNext(new String[]{f.getContig(), String.valueOf(f.getStart() - 1), String.valueOf(f.getEnd()), f.getName(), score, (f.getStrand() == Strand.NEGATIVE ? "-" : f.getStrand() == Strand.POSITIVE ? "+" : null)});
                            unmapped++;
                        }
                    }

                    NumberFormat pctFormat = NumberFormat.getPercentInstance();
                    pctFormat.setMaximumFractionDigits(1);

                    job.getLogger().info("total mapped: " + mapped + " (" + pctFormat.format(mapped / (mapped + unmapped)) + "%)");
                    job.getLogger().info("total unmapped: " + unmapped + " (" + pctFormat.format(unmapped / (mapped + unmapped)) + "%)");
                }
            }
        }

        //sort resulting file
        SequenceUtil.sortROD(output, job.getLogger(), 2);
    }
}
