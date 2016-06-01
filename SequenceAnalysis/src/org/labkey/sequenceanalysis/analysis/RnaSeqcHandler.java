package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.RnaSeQCWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 9/8/2014.
 */
public class RnaSeqcHandler extends AbstractParameterizedOutputHandler
{
    private FileType _bamFileType = new FileType("bam", false);

    public RnaSeqcHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "RNA-SeQC", "This will run RNA-SeQC on the selected BAMs, which produces quality metric reports.", new LinkedHashSet<>(Arrays.asList("sequenceanalysis/field/GenomeFileSelectorField.js")), Arrays.asList(
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
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-ttype"), "ttype", "Transcript Column", "The column in GTF to use to look for rRNA transcript type. Mainly used for running on Ensembl GTF (specify '-ttype 2'). Otherwise, for spec-conforming GTF files, disregard.", "ldk-integerfield", null, 2)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getLibrary_id() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
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
            if (!params.containsKey("name"))
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

                    support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(o.getLibrary_id(), job.getUser()));
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
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String name = params.getString("name");

            int gtf = params.optInt("gtf");
            if (gtf == 0)
            {
                throw new PipelineJobException("No GTF file provided");
            }

            File gtfFile = support.getCachedData(gtf);
            if (gtfFile == null || !gtfFile.exists())
            {
                throw new PipelineJobException("Unable to find GTF file: " + gtfFile);
            }

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            action.addInput(gtfFile, "GTF file");

            //always run as batch
            List<File> bams = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            inputFiles = new ArrayList<>(inputFiles); //make sure mutable
            Collections.sort(inputFiles, new Comparator<SequenceOutputFile>()
            {
                @Override
                public int compare(SequenceOutputFile o1, SequenceOutputFile o2)
                {
                    return o1.getName().compareTo(o2.getName());
                }
            });

            for (SequenceOutputFile o : inputFiles)
            {
                bams.add(o.getFile());
                sampleIds.add(o.getName());
                notes.add(o.getDescription());
            }

            job.getLogger().info("running RNA-SeQC");
            job.setStatus(PipelineJob.TaskStatus.running, "RUNNING RNA-SeQC");

            List<String> extraParams = getClientCommandArgs(params);

            RnaSeQCWrapper wrapper = new RnaSeQCWrapper(job.getLogger());
            ReferenceGenome g = support.getCachedGenome(inputFiles.get(0).getLibrary_id());
            File outputReport = wrapper.execute(bams, sampleIds, notes, g.getWorkingFastaFile(), gtfFile, outputDir, name, extraParams);

            File indexHtml = new File(outputReport, "index.html");
            SequenceOutputFile so = new SequenceOutputFile();
            so.setCategory("RNA-SeQC Report");
            so.setFile(indexHtml);
            so.setContainer(job.getContainerId());
            so.setName(params.getString("name"));
            outputsToCreate.add(so);

            action.setEndTime(new Date());
            actions.add(action);
        }
    }
}
