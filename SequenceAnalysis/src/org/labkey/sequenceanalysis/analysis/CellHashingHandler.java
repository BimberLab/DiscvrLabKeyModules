package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public CellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "CITE-Seq Count", "This will run CITE-Seq Count to generate a table of features counts from CITE-Seq or cell hashing libraries", null, getDefaultParams());
    }

    public static List<ToolParameterDescriptor> getDefaultParams()
    {
        return Arrays.asList(
                ToolParameterDescriptor.create("outputFilePrefix", "Output File Basename", null, "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, "hashTagCounts"),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbf"), "cbf", "Cell Barcode Start", null, "ldk-integerfield", null, 1),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbl"), "cbl", "Cell Barcode End", null, "ldk-integerfield", null, 16),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umif"), "umif", "UMI Start", null, "ldk-integerfield", null, 17),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umil"), "umil", "UMI End", null, "ldk-integerfield", null, 26),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-hd"), "hd", "Edit Distance", null, "ldk-integerfield", null, 1),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cells"), "cells", "Expected Cells", null, "ldk-integerfield", null, 8000),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-tr"), "tr", "RegEx", null, "textfield", null, "^[ATGCN]{15}"),
                ToolParameterDescriptor.create("tagGroup", "Tag List", null, "ldk-simplelabkeycombo", new JSONObject(){{
                    put("schemaName", "sequenceanalysis");
                    put("queryName", "barcode_groups");
                    put("displayField", "group_name");
                    put("valueField", "group_name");
                    put("allowBlank", false);
                }}, "5p-HTOs")
        );
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
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
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor();
    }

    private File getBarcodeFile(File outputDir)
    {
        return new File(outputDir, "barcodes.txt");
    }

    protected class Processor implements SequenceReadsetProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String tagGroup = params.getString("tagGroup");

            File outputFile = getBarcodeFile(outputDir);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), ','))
            {
                TableInfo ti = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_BARCODES);
                new TableSelector(ti, new SimpleFilter(FieldKey.fromString("group_name"), tagGroup), null).forEachResults(rs -> {
                    writer.writeNext(new String[]{rs.getString("sequence"), rs.getString("tag_name")});
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<String> args = new ArrayList<>();

            args.addAll(getClientCommandArgs(ctx.getParams()));
            args.add("-t");
            args.add(getBarcodeFile(ctx.getSourceDirectory()).getPath());

            for (Readset rs : readsets)
            {
                CiteSeqCountWrapper wrapper = new CiteSeqCountWrapper(ctx.getLogger());
                if (rs.getReadData().size() != 1)
                {
                    throw new PipelineJobException("This tool expects each readset to have exactly one read pair.  was: " + rs.getReadData().size());
                }

                ReadData rd = rs.getReadData().get(0);

                String fn = ctx.getParams().getString("outputFilePrefix");

                args.add("-u");
                args.add(new File(ctx.getOutputDir(), "citeSeqUnknownBarcodes.txt").getPath());

                File output1 = new File(ctx.getOutputDir(), "citeSeqCounts.txt");
                wrapper.execute(args, rd.getFile1(), rd.getFile2(), output1);

                if (!output1.exists())
                {
                    throw new PipelineJobException("Unable to find expected file: " + output1.getPath());
                }

                generalFinalCalls(output1, ctx.getOutputDir(), fn, ctx.getLogger());

                File output2 = new File(ctx.getOutputDir(), fn + ".txt");
                ctx.getFileManager().addSequenceOutput(output2, rs.getName() + ": CITE-seq","CITE-seq Cell Calls", rs.getReadsetId(), null, null, null);
            }
        }
    }

    public void generalFinalCalls(File citeSeqCountsOutput, File outputDir, String basename, Logger log) throws PipelineJobException
    {
        String summaryScript = getScriptPath("sequenceanalysis", "/external/multiSeqClassifier.R");
        String summaryScriptWrapper = getScriptPath("sequenceanalysis", "/external/multiSeqClassifier.sh");

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(log);
        rWrapper.setWorkingDir(outputDir);
        File outputFile = new File(outputDir, basename);
        rWrapper.execute(Arrays.asList("/bin/bash", summaryScriptWrapper, summaryScript, citeSeqCountsOutput.getPath(), outputFile.getPath()));
        outputFile = new File(outputFile.getPath() + ".txt");

        //summarize results:
        try (CSVReader reader = new CSVReader(Readers.getReader(outputFile), '\t'))
        {
            Map<String, Long> counts = new HashMap<>();
            String[] line;
            int idx = 0;
            while ((line = reader.readNext()) != null)
            {
                idx++;
                if (idx == 1)
                {
                    continue;
                }

                String tag = line[1];
                counts.put(tag, 1 + counts.getOrDefault(tag, 0L));
            }

            log.info("HTO summary:");
            for (String tag : counts.keySet())
            {
                log.info(tag + ": " + counts.get(tag));
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private String getScriptPath(String moduleName, String path) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(moduleName);
        Resource script = module.getModuleResource(path);
        if (script == null || !script.exists())
            throw new PipelineJobException("Unable to find file: " + script.getPath() + " in module: " + moduleName);

        File f = ((FileResource) script).getFile();
        if (!f.exists())
            throw new PipelineJobException("Unable to find file: " + f.getPath());

        return f.getPath();
    }

    public static class CiteSeqCountWrapper extends AbstractCommandWrapper
    {
        public CiteSeqCountWrapper(Logger log)
        {
            super(log);
        }

        public void execute(List<String> params, File fq1, File fq2, File output) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.addAll(params);

            args.add("-R1");
            args.add(fq1.getPath());

            if (fq2 != null)
            {
                args.add("-R2");
                args.add(fq2.getPath());
            }

            args.add("-o");
            args.add(output.getPath());

            execute(args);
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("CITESEQCOUNTPATH", "CITE-seq-Count");
        }
    }
}
