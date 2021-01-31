package org.labkey.singlecell.analysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.SingleCellSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public CellHashingHandler()
    {
        this("Cell Hashing Calls", "This will run CITE-Seq Count to generate a table of features counts from CITE-Seq or cell hashing libraries. It will also run R code to generate a table of calls per cell", getDefaultParams(CellHashingService.BARCODE_TYPE.hashing));
    }

    protected CellHashingHandler(String name, String description, List<ToolParameterDescriptor> defaultParams)
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), name, description, null, defaultParams);
    }

    public static List<ToolParameterDescriptor> getDefaultParams(CellHashingService.BARCODE_TYPE type)
    {
        return getDefaultParams(true, type);
    }

    public static List<ToolParameterDescriptor> getDefaultParams(boolean allowScanningEditDistance, CellHashingService.BARCODE_TYPE type)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create("outputFilePrefix", "Output File Basename", null, "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, type.getDefaultName()),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbf"), "cbf", "Cell Barcode Start", null, "ldk-integerfield", null, 1),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbl"), "cbl", "Cell Barcode End", null, "ldk-integerfield", null, 16),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umif"), "umif", "UMI Start", null, "ldk-integerfield", null, 17),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umil"), "umil", "UMI End", null, "ldk-integerfield", null, 26),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-trim"), "trim", "Trim", null, "ldk-integerfield", null, type.getDefaultTrim())
        ));

        if (allowScanningEditDistance)
        {
            ret.add(ToolParameterDescriptor.create("scanEditDistances", "Scan Edit Distances", "If checked, CITE-seq-count will be run using edit distances from 0-3 and the iteration with the highest singlets will be used.", "checkbox", new JSONObject()
            {{
                put("checked", false);
            }}, false));
        }

        ret.addAll(Arrays.asList(
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 2),
                ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, false),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cells"), "cells", "Expected Cells", null, "ldk-integerfield", null, 20000),
                ToolParameterDescriptor.create("tagGroup", "Tag List", null, "ldk-simplelabkeycombo", new JSONObject(){{
                    put("schemaName", "sequenceanalysis");
                    put("queryName", "barcode_groups");
                    put("displayField", "group_name");
                    put("valueField", "group_name");
                    put("allowBlank", false);
                }}, type.getDefaultTagGroup()),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        ));

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
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
    public boolean requiresSingleGenome()
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
        return new Processor(CellHashingService.BARCODE_TYPE.hashing);
    }

    public class Processor implements SequenceReadsetProcessor
    {
        private final CellHashingService.BARCODE_TYPE _type;

        public Processor(CellHashingService.BARCODE_TYPE type)
        {
            _type = type;
        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String tagGroup = params.getString("tagGroup");
            CellHashingServiceImpl.get().writeAllBarcodes(_type, outputDir, job.getUser(), job.getContainer(), tagGroup);
        }

        @Override
        public void complete(PipelineJob job, List<Readset> readsets, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            TableInfo ti = SingleCellSchema.getInstance().getSequenceAnalysisSchema().getTable(SingleCellSchema.TABLE_QUALITY_METRICS);
            for (SequenceOutputFile so : outputsCreated)
            {
                job.getLogger().info("Saving quality metrics for: " + so.getName());

                //NOTE: if this job errored and restarted, we may have duplicate records:
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), so.getReadset());
                filter.addCondition(FieldKey.fromString("category"), "Cell Hashing", CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("dataid"), so.getDataId(), CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("container"), job.getContainer().getId(), CompareType.EQUAL);
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
                if (ts.exists())
                {
                    job.getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                    ts.getArrayList(Integer.class).forEach(rowid -> {
                        Table.delete(ti, rowid);
                    });
                }

                if (so.getFile().getName().endsWith(CellHashingServiceImpl.CALL_EXTENSION))
                {
                    Map<String, Object> counts = CellHashingServiceImpl.get().parseOutputTable(job.getLogger(), so.getFile(), CellHashingServiceImpl.get().getCiteSeqCountUnknownOutput(so.getFile().getParentFile(), _type, null), so.getFile().getParentFile(), null, false, _type);
                    for (String name : counts.keySet())
                    {
                        String valueField = (counts.get(name) instanceof String) ? "qualvalue" : "metricvalue";

                        Map<String, Object> r = new HashMap<>();
                        r.put("category", "Cell Hashing");
                        r.put("metricname", StringUtils.capitalize(name));
                        r.put(valueField, counts.get(name));
                        r.put("dataid", so.getDataId());
                        r.put("readset", so.getReadset());

                        r.put("container", job.getContainer());
                        r.put("createdby", job.getUser().getUserId());

                        Table.insert(job.getUser(), ti, r);
                    }
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            ctx.addActions(action);

            for (Readset rs : readsets)
            {
                CellHashingService.CellHashingParameters parameters = CellHashingService.CellHashingParameters.createFromJson(_type, ctx.getSourceDirectory(), ctx.getParams(), rs, null, null);
                CellHashingServiceImpl.get().processCellHashingOrCiteSeq(ctx, parameters);
            }
        }
    }

    public static class CiteSeqCountWrapper extends AbstractCommandWrapper
    {
        public CiteSeqCountWrapper(Logger log)
        {
            super(log);
        }

        public void execute(List<String> params, File fq1, File fq2, File outputDir) throws PipelineJobException
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
            args.add(outputDir.getPath());

            String output = executeWithOutput(args);
            if (output.contains("format requires -2147483648 <= number"))
            {
                throw new PipelineJobException("Error running Cite-seq-count. Repeat using more cores");
            }
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("CITESEQCOUNTPATH", "CITE-seq-Count");
        }
    }
}
