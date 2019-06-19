package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.SequenceJobSupportImpl;
import org.labkey.sequenceanalysis.run.alignment.CellRangerWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerAggrHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);

    public CellRangerAggrHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "CellRanger Aggr", "This will aggregate the selected files into a single cloupe file.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/CellRangerAggrTextarea.js")), Arrays.asList(
                ToolParameterDescriptor.create("id", "Run ID", "This will be used as the final sample/file name", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("csvData", "CSV Data", "Provide a table that maps readset to additional category fields.  This will be provided directly to cellranger aggr.  The first column must be the output file ID, which is how the system connects your input to these files.  We recommend cut and pasting this text into excel, editing, and then pasting back.", "sequenceanalysis-aggr-textarea", new JSONObject(){{
                    put("checked", true);
                }}, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
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
        return new CellRangerAggrHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() != null)
                {
                    ((SequenceJobSupportImpl) support).cacheReadset(so.getReadset(), job.getUser());
                }
                else
                {
                    job.getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
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
            try
            {
                Set<ReferenceGenome> genomes = new HashSet<>();
                File csv = new File(ctx.getOutputDir(), "samples.csv");
                Map<Integer, File> matrixMap = new HashMap<>();
                Map<Integer, String> nameMap = new HashMap<>();
                for (SequenceOutputFile so : inputFiles)
                {
                    File matrix = new File(so.getFile().getParentFile(), "molecule_info.h5");
                    if (!matrix.exists())
                    {
                        throw new PipelineJobException("Unable to find gene count matrix: " + matrix.getPath());
                    }

                    matrixMap.put(so.getRowid(), matrix);
                    nameMap.put(so.getRowid(), so.getName());
                    genomes.add(ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()));
                }

                ctx.getLogger().debug(ctx.getParams().getString("csvData"));
                String[] csvStr = ctx.getParams().getString("csvData").split("<>");

                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(csv), ','))
                {
                    int idx = 0;
                    for (String line : csvStr)
                    {
                        List<String> toAdd = new ArrayList<>(Arrays.asList(line.split(",")));

                        idx++;
                        if (idx == 1)
                        {
                            toAdd.add(0, "library_id");
                            toAdd.add(1, "molecule_h5");
                        }
                        else
                        {
                            Integer rowId = Integer.parseInt(toAdd.get(0));
                            File matrix = matrixMap.get(rowId);
                            if (matrix == null)
                            {
                                throw new PipelineJobException("No matrix found for ID: " + rowId);
                            }

                            toAdd.add(0, nameMap.get(rowId));
                            toAdd.add(1, matrix.getPath());
                        }

                        writer.writeNext(toAdd.toArray(new String[0]));
                    }
                }

                List<String> extraArgs = new ArrayList<>();
                Integer cores = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                if (cores != null)
                {
                    extraArgs.add("--localcores=" + cores);
                }

                CellRangerWrapper wrapper = new CellRangerWrapper(ctx.getLogger());
                wrapper.setWorkingDir(ctx.getOutputDir());
                wrapper.setOutputDir(ctx.getOutputDir());

                String id = ctx.getParams().getString("id");
                File output = wrapper.runAggr(id, csv, extraArgs);

                ReferenceGenome referenceGenome = genomes.iterator().next();

                File outs = output.getParentFile();

                File outputHtml = new File(outs, "web_summary.html");
                if (!outputHtml.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                }

                ctx.getFileManager().addSequenceOutput(outputHtml, id + " 10x Aggr Summary", "10x Run Summary", null, null, referenceGenome.getGenomeId(), null);

                File loupe = new File(outs, "cloupe.cloupe");
                if (loupe.exists())
                {
                    File loupeRename = new File(outs, id + "_" + loupe.getName());
                    FileUtils.moveFile(loupe, loupeRename);
                    ctx.getFileManager().addSequenceOutput(loupeRename, id + " 10x Aggr Loupe File", "10x Loupe File", null, null, referenceGenome.getGenomeId(), null);
                }
                else
                {
                    ctx.getLogger().info("loupe file not found: " + loupe.getPath());
                }

                File scDir = new File(outs.getParentFile(), "SC_RNA_AGGREGATOR_CS");
                if (scDir.exists())
                {
                    //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                    new SimpleScriptWrapper(ctx.getLogger()).execute(Arrays.asList("rm", "-Rf", scDir.getPath()));
                }
                else
                {
                    ctx.getLogger().warn("Unable to find folder: " + scDir.getPath());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
