package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.RnaSeQCWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 9/8/2014.
 */
public class RnaSeqcHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _bamFileType = new FileType("bam", false);

    public RnaSeqcHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "RNA-SeQC", "This will run RNA-SeQC on the selected BAMs, which produces quality metric reports.", new LinkedHashSet<>(Arrays.asList("sequenceanalysis/field/GenomeFileSelectorField.js", "ldk/field/ExpDataField.js")), Arrays.asList(
                ToolParameterDescriptor.create("name", "Output Name", "This is the name that will be used to describe the output.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "top_transcripts", "Top Transcripts", "Number of top transcripts to use. Default is 1000.", "ldk-integerfield", null, 1000),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-singleEnd"), "singleEnd", "Single Ended Reads?", "This BAM contains single end reads.", "checkbox", null, false),
                ToolParameterDescriptor.createExpDataParam("gtf", "GTF File", "The GTF file containing genes for this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-ttype"), "ttype", "Transcript Column", "The column in GTF to use to look for rRNA transcript type. Mainly used for running on Ensembl GTF (specify '-ttype 2'). Otherwise, for spec-conforming GTF files, disregard.", "ldk-integerfield", null, 2),
                ToolParameterDescriptor.createExpDataParam("BWArRNA", "rRNA FASTA", "The dataId of a file representing rRNA sequence.  Use an on the fly BWA alignment for estimating rRNA content. The value should be the rRNA reference fasta. If this flag is absent, rRNA estimation will be based upon the rRNA transcript intervals provided in the GTF (a faster but less robust method).", "ldk-expdatafield", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getLibrary_id() != null && _bamFileType.isType(o.getFile());
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
            if (!ctx.getParams().containsKey("name"))
            {
                throw new PipelineJobException("Must provide the name of the output");
            }

            Integer libraryId = null;
            for (SequenceOutputFile o : inputFiles)
            {
                if (o.getLibrary_id() != null)
                {
                    if (libraryId == null)
                    {
                        libraryId = o.getLibrary_id();
                    }

                    if (!libraryId.equals(o.getLibrary_id()))
                    {
                        throw new PipelineJobException("All samples must use the same reference genome");
                    }

                    ctx.getSequenceSupport().cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), ctx.getJob().getUser()));
                }
                else
                {
                    throw new PipelineJobException("No library Id provided for file: " + o.getRowid());
                }
            }
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

            String name = params.getString("name");

            int gtf = params.optInt("gtf");
            if (gtf == 0)
            {
                throw new PipelineJobException("No GTF file provided");
            }

            File gtfFile = ctx.getSequenceSupport().getCachedData(gtf);
            if (gtfFile == null || !gtfFile.exists())
            {
                throw new PipelineJobException("Unable to find GTF file: " + gtfFile);
            }

            File rnaFasta = null;
            if (!StringUtils.isEmpty(params.optString("BWArRNA")))
            {
                rnaFasta = ctx.getSequenceSupport().getCachedData(params.getInt("BWArRNA"));
                if (!rnaFasta.exists())
                {
                    throw new PipelineJobException("Unable to find fasta file: " + rnaFasta.getPath());
                }
            }

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            action.addInput(gtfFile, "GTF file");

            //always run as batch
            List<File> bams = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            inputFiles = new ArrayList<>(inputFiles); //make sure mutable
            inputFiles.sort(Comparator.comparing(SequenceOutputFile::getName));

            for (SequenceOutputFile o : inputFiles)
            {
                bams.add(o.getFile());
                sampleIds.add(o.getName());
                //Note: this has contain newlines and is generally not especially useful, so omit it.
                //notes.add(o.getDescription());
            }

            job.getLogger().info("running RNA-SeQC");
            job.setStatus(PipelineJob.TaskStatus.running, "RUNNING RNA-SeQC");

            List<String> extraParams = getClientCommandArgs(params);

            RnaSeQCWrapper wrapper = new RnaSeQCWrapper(job.getLogger());
            ReferenceGenome g = ctx.getSequenceSupport().getCachedGenome(inputFiles.get(0).getLibrary_id());
            File outputReport = wrapper.execute(bams, sampleIds, notes, g.getWorkingFastaFile(), gtfFile, ctx.getOutputDir(), name, extraParams, rnaFasta);

            File localBwaDir =new File(ctx.getOutputDir(), "bwaIndex");
            if (localBwaDir.exists())
            {
                ctx.getFileManager().addIntermediateFile(localBwaDir);
            }

            File indexHtml = new File(outputReport, "index.html");
            SequenceOutputFile so = new SequenceOutputFile();
            so.setCategory("RNA-SeQC Report");
            so.setFile(indexHtml);
            so.setContainer(job.getContainerId());
            so.setName(params.getString("name"));
            so.setLibrary_id(g.getGenomeId());
            ctx.addSequenceOutput(so);

            action.setEndTime(new Date());
            ctx.addActions(action);
        }
    }
}
