package org.labkey.singlecell.analysis;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractResumer;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellStep;
import org.labkey.api.singlecell.pipeline.SingleCellRawDataStep;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.pipeline.singlecell.AbstractCellMembraneStep;
import org.labkey.singlecell.pipeline.singlecell.PrepareRawCounts;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class AbstractSingleCellHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames
{
    public static final String SEURAT_PROTOTYPE = "Seurat Object Prototype";

    protected Resumer _resumer;

    public AbstractSingleCellHandler()
    {

    }

    @Override
    public String getDescription()
    {
        return "Run one or more tools to process 10x scRNA-seq Data";
    }

    @Override
    public String getAnalysisType(PipelineJob job)
    {
        if (!job.getParameters().containsKey("singleCell"))
        {
            return SequenceOutputHandler.super.getAnalysisType(job);
        }

        return Arrays.asList(job.getParameters().get("singleCell").split(";")).contains("SeuratPrototype") ? "Seurat Prototype" : SequenceOutputHandler.super.getAnalysisType(job);
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SingleCellModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doSplitJobs()
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
    public Collection<String> getAllowableActionNames()
    {
        Set<String> allowableNames = new HashSet<>();
        for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(SingleCellStep.class))
        {
            allowableNames.add(provider.getLabel());
        }

        allowableNames.add(PrepareRawCounts.LABEL);

        return allowableNames;
    }

    public class Processor implements SequenceOutputProcessor
    {
        private final boolean _doProcessRawCounts;

        public Processor(boolean doProcessRawCounts)
        {
            _doProcessRawCounts = doProcessRawCounts;
        }

        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            List<PipelineStepCtx<SingleCellRawDataStep>> rawCountSteps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellRawDataStep.class);
            if (!rawCountSteps.isEmpty())
            {
                for (PipelineStepCtx<SingleCellRawDataStep> stepCtx : rawCountSteps)
                {
                    SingleCellRawDataStep step = stepCtx.getProvider().create(ctx);
                    step.init(ctx, inputFiles);
                }
            }

            boolean requiresHashing = false;
            boolean requiresCite = false;
            boolean doH5Caching = false;
            List<PipelineStepCtx<SingleCellStep>> steps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellStep.class);
            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                SingleCellStep step = stepCtx.getProvider().create(ctx);
                step.init(ctx, inputFiles);

                if (step.requiresCiteSeq(ctx))
                {
                    requiresCite = true;
                }

                if (step.requiresHashing(ctx))
                {
                    String methods = step.getProvider().hasParameter("methods") ? step.getProvider().getParameterByName("methods").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), String.class) : null;
                    if (CellHashingService.CALLING_METHOD.requiresH5(methods))
                    {
                        doH5Caching = true;
                    }

                    requiresHashing = true;
                }

                for (ToolParameterDescriptor pd : stepCtx.getProvider().getParameters())
                {
                    if (pd instanceof ToolParameterDescriptor.CachableParam)
                    {
                        ctx.getLogger().debug("caching params for : " + pd.getName()+ ", with step idx: " + stepCtx.getStepIdx());
                        Object val = pd.extractValue(ctx.getJob(), stepCtx.getProvider(), stepCtx.getStepIdx(), Object.class);
                        ((ToolParameterDescriptor.CachableParam)pd).doCache(ctx.getJob(), val, ctx.getSequenceSupport());
                    }
                }
            }

            if (requiresCite || requiresHashing)
            {
                for (SequenceOutputFile f : inputFiles)
                {
                    if (f.getReadset() == null && f.getFile() != null && f.getFile().getPath().toLowerCase().endsWith(".rds"))
                    {
                        ctx.getLogger().info("Seurat object lacks a readset, so attempting to infer from cellbarcodes");

                        //NOTE: for a multi-dataset object, readset might be null, but the component loupe files might map to multiple readsets. For ease, try to lookup and recover the component readsets:
                        File barcodeFile = CellHashingServiceImpl.get().getCellBarcodesFromSeurat(f.getFile(), false);
                        if (barcodeFile.exists())
                        {
                            Set<Integer> uniquePrefixes = new HashSet<>();
                            try (CSVReader reader = new CSVReader(Readers.getReader(barcodeFile), '\t'))
                            {
                                String[] line;
                                while ((line = reader.readNext()) != null)
                                {
                                    String[] tokens = line[0].split("_");
                                    if (tokens.length > 1)
                                    {
                                        uniquePrefixes.add(Integer.parseInt(tokens[0]));
                                    }
                                }
                            }
                            catch (IOException e)
                            {
                                throw new PipelineJobException(e);
                            }

                            ctx.getLogger().info("Total dataset Ids: " + uniquePrefixes.size());
                            for (int loupeId : uniquePrefixes)
                            {
                                SequenceOutputFile so = SequenceOutputFile.getForId(loupeId);
                                if (so == null)
                                {
                                    throw new PipelineJobException("Unable to find loupe file for: " + loupeId);
                                }
                                else if (so.getReadset() == null)
                                {
                                    throw new PipelineJobException("Readset is blank for loupe file: " + loupeId);
                                }

                                ctx.getSequenceSupport().cacheReadset(so.getReadset(), ctx.getJob().getUser());
                            }
                        }
                        else
                        {
                            ctx.getLogger().warn("Barcode file not found: " + barcodeFile.getPath());
                        }
                    }
                }

                CellHashingServiceImpl.get().prepareHashingAndCiteSeqFilesIfNeeded(ctx.getSourceDirectory(), ctx.getJob(), ctx.getSequenceSupport(), "readsetId", false, false, true, requiresHashing, requiresCite, doH5Caching);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if ("Seurat Cell Hashing Calls".equals(so.getCategory()))
                {
                    job.getLogger().info("Adding metrics for output: " + so.getName());
                    CellHashingService.get().processMetrics(so, job, true);
                }

                if (SEURAT_PROTOTYPE.equals(so.getCategory()))
                {
                    //NOTE: upstream we enforce one dataset per job, so we can safely assume this is the only dataset here:
                    File metricFile = new File(job.getLogFile().getParentFile(), "seurat.metrics.txt");
                    if (metricFile.exists())
                    {
                        processMetrics(so, job, metricFile);
                    }
                    else
                    {
                        job.getLogger().info("Metrics file not found, skipping");
                    }
                }
            }
        }

        private void processMetrics(SequenceOutputFile so, PipelineJob job, File metricsFile) throws PipelineJobException
        {
            job.getLogger().info("Loading metrics");
            int total = 0;
            TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

            //NOTE: if this job errored and restarted, we may have duplicate records:
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), so.getReadset());
            filter.addCondition(FieldKey.fromString("analysis_id"), so.getAnalysis_id(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("dataid"), so.getDataId(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("category"), "SeuratMetrics", CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("container"), job.getContainer().getId(), CompareType.EQUAL);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
            if (ts.exists())
            {
                job.getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
                ts.getArrayList(Integer.class).forEach(rowid -> {
                    Table.delete(ti, rowid);
                });
            }

            try (CSVReader reader = new CSVReader(Readers.getReader(metricsFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    Map<String, Object> r = new HashMap<>();
                    r.put("category", "SeuratMetrics");
                    r.put("metricname", line[2]);

                    //NOTE: R saves NaN as NA.  This is fixed in the R code, but add this check here to let existing jobs import
                    String value = line[3];
                    if ("NA".equals(value))
                    {
                        value = "0";
                    }

                    String fieldName = NumberUtils.isCreatable(value) ? "metricvalue" : "qualvalue";
                    r.put(fieldName, value);

                    r.put("analysis_id", so.getAnalysis_id());
                    r.put("dataid", so.getDataId());
                    r.put("readset", so.getReadset());
                    r.put("container", job.getContainer());
                    r.put("createdby", job.getUser().getUserId());

                    Table.insert(job.getUser(), ti, r);
                    total++;
                }

                job.getLogger().info("total metrics: " + total);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            _resumer = Resumer.create(ctx);

            Map<Integer, SequenceOutputFile> inputMap = inputFiles.stream().collect(Collectors.toMap(SequenceOutputFile::getRowid, so -> so));

            List<PipelineStepCtx<SingleCellStep>> steps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellStep.class);

            String basename;
            if (inputFiles.size() == 1 && inputFiles.get(0).getReadset() != null)
            {
                Integer readsetId = inputFiles.get(0).getReadset();
                Readset rs = ctx.getSequenceSupport().getCachedReadset(readsetId);
                basename = FileUtil.makeLegalName(rs.getName()).replaceAll(" ", "_");
            }
            else
            {
                basename = "SingleCell";
            }

            List<SingleCellStep.SeuratObjectWrapper> currentFiles;
            Set<File> originalInputs = inputFiles.stream().map(SequenceOutputFile::getFile).collect(Collectors.toSet());
            Set<File> originalRDSCopiedLocal = new HashSet<>();
            if (_doProcessRawCounts)
            {
                currentFiles = processRawCounts(ctx, inputFiles, basename);
            }
            else
            {
                try
                {
                    Set<String> distinctIds = new HashSet<>();
                    Set<String> copiedFiles = new HashSet<>();

                    currentFiles = new ArrayList<>();
                    for (SequenceOutputFile so : inputFiles)
                    {
                        String datasetId = FileUtil.makeLegalName(so.getReadset() != null ? ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName() : so.getName());
                        if (distinctIds.contains(datasetId))
                        {
                            throw new PipelineJobException("Duplicate dataset Ids in input data: " + datasetId);
                        }
                        distinctIds.add(datasetId);

                        //ensure local copy:
                        if (copiedFiles.contains(so.getFile().getName()))
                        {
                            throw new PipelineJobException("Duplicate files names in input data: " + so.getFile().getName());
                        }
                        copiedFiles.add(so.getFile().getName());

                        File local = new File(ctx.getOutputDir(), so.getFile().getName());
                        if (local.exists())
                        {
                            local.delete();
                        }

                        FileUtils.copyFile(so.getFile(), local);
                        _resumer.getFileManager().addIntermediateFile(local);

                        File cellBarcodes = CellHashingServiceImpl.get().getCellBarcodesFromSeurat(so.getFile(), false);
                        if (cellBarcodes.exists())
                        {
                            ctx.getLogger().debug("Also making local copy of cellBarcodes TSV: " + cellBarcodes.getPath());
                            File cellBarcodesLocal = new File(ctx.getOutputDir(), cellBarcodes.getName());
                            if (cellBarcodesLocal.exists())
                            {
                                cellBarcodesLocal.delete();
                            }

                            FileUtils.copyFile(cellBarcodes, cellBarcodesLocal);
                            _resumer.getFileManager().addIntermediateFile(cellBarcodesLocal);
                        }
                        else
                        {
                            ctx.getLogger().debug("cellBarcodes TSV not found, expected: " + cellBarcodes.getPath());
                        }

                        File metadataFile = CellHashingServiceImpl.get().getMetaTableFromSeurat(so.getFile(), false);
                        if (metadataFile.exists())
                        {
                            ctx.getLogger().debug("Also making local copy of metadata TSV: " + metadataFile.getPath());
                            File metadataFileLocal = new File(ctx.getOutputDir(), metadataFile.getName());
                            if (metadataFileLocal.exists())
                            {
                                metadataFileLocal.delete();
                            }

                            FileUtils.copyFile(metadataFile, metadataFileLocal);
                            _resumer.getFileManager().addIntermediateFile(metadataFileLocal);
                        }
                        else
                        {
                            ctx.getLogger().warn("metadataFile TSV not found, expected: " + metadataFile.getPath());
                        }
                        
                        currentFiles.add(new SingleCellStep.SeuratObjectWrapper(datasetId, datasetId, local, so));
                    }

                    originalRDSCopiedLocal.addAll(currentFiles.stream().map(AbstractSingleCellStep.SeuratObjectWrapper::getFile).collect(Collectors.toSet()));
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            // Step 2: iterate seurat processing:
            String outputPrefix = basename;
            int stepIdx = 0;
            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                ctx.getLogger().info("Starting to run: " + stepCtx.getProvider().getLabel());
                stepIdx++;

                SingleCellStep step = stepCtx.getProvider().create(ctx);
                step.setStepIdx(stepCtx.getStepIdx());

                if (!step.isIncluded(ctx, inputFiles))
                {
                    ctx.getLogger().info("Step not required, skipping");
                    continue;
                }

                // NOTE: always set this upfront, so we have consistent outputPrefix after resume:
                outputPrefix = outputPrefix + "." + step.getFileSuffix() + (step.getStepIdx() == 0 ? "" : "-" + step.getStepIdx());
                outputPrefix = outputPrefix.replaceAll(" ", "_");

                if (_resumer.isStepComplete(stepIdx))
                {
                    ctx.getLogger().info("resuming from saved state");
                    if (_resumer.getSeuratFromStep(stepIdx) != null)
                    {
                        currentFiles = _resumer.getSeuratFromStep(stepIdx);
                    }
                    else if (step.createsSeuratObjects())
                    {
                        throw new PipelineJobException("Expected step to create seurat objects but none were cached");
                    }
                    else
                    {
                        ctx.getLogger().debug("No cached seurat objects found from step, using prior step's output");
                    }

                    continue;
                }

                ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + stepCtx.getProvider().getLabel());
                currentFiles.forEach(currentFile -> {
                    if (currentFile.getSequenceOutputFileId() != null)
                    {
                        currentFile.setSequenceOutputFile(inputMap.get(currentFile.getSequenceOutputFileId()));
                    }
                });

                RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                Date start = new Date();
                action.setStartTime(start);
                currentFiles.forEach(currentFile -> action.addInput(currentFile.getFile(), "Input Seurat Object"));
                ctx.getFileManager().addIntermediateFiles(currentFiles.stream().map(SingleCellStep.SeuratObjectWrapper::getFile).collect(Collectors.toList()));

                SingleCellStep.Output output = step.execute(ctx, currentFiles, outputPrefix);

                _resumer.getFileManager().addStepOutputs(action, output);

                if (step.createsSeuratObjects())
                {
                    if (output.getSeuratObjects() != null && !output.getSeuratObjects().isEmpty())
                    {
                        currentFiles = new ArrayList<>(output.getSeuratObjects());
                        Set<File> possibleIntermediates = currentFiles.stream().map(SingleCellStep.SeuratObjectWrapper::getFile).collect(Collectors.toSet());
                        possibleIntermediates.removeAll(originalInputs);
                        if (!possibleIntermediates.isEmpty())
                        {
                            _resumer.getFileManager().addIntermediateFiles(possibleIntermediates);
                            _resumer.getFileManager().addIntermediateFiles(possibleIntermediates.stream().map(x -> CellHashingServiceImpl.get().getCellBarcodesFromSeurat(x)).collect(Collectors.toSet()));
                            _resumer.getFileManager().addIntermediateFiles(possibleIntermediates.stream().map(x -> CellHashingServiceImpl.get().getMetaTableFromSeurat(x)).collect(Collectors.toSet()));
                        }
                    }
                    else
                    {
                        throw new PipelineJobException("Expected step to create seurat objects but none reported");
                    }
                }
                else
                {
                    ctx.getLogger().info("No seurat objects were produced");
                }

                Date end = new Date();
                action.setEndTime(end);
                ctx.getJob().getLogger().info(stepCtx.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

                _resumer.setStepComplete(ctx.getLogger(), step, stepIdx, action, currentFiles, output.getMarkdownFile(), output.getHtmlFile());
            }

            for (SingleCellStep.SeuratObjectWrapper seurat : currentFiles)
            {
                if (seurat.getFile().exists())
                {
                    ctx.getLogger().debug("Removing intermediate file: " + seurat.getFile().getPath());
                    ctx.getFileManager().removeIntermediateFile(seurat.getFile());
                    _resumer.getFileManager().removeIntermediateFile(seurat.getFile());
                    _resumer.getFileManager().removeIntermediateFile(CellHashingServiceImpl.get().getCellBarcodesFromSeurat(seurat.getFile(), false));
                    _resumer.getFileManager().removeIntermediateFile(CellHashingServiceImpl.get().getMetaTableFromSeurat(seurat.getFile(), false));
                }
            }

            if (_resumer.getMarkdowns().isEmpty())
            {
                throw new PipelineJobException("No markdown files produced!");
            }

            originalInputs.forEach(x -> _resumer.getFileManager().removeIntermediateFile(x));

            //process with pandoc
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Creating final report");
            AbstractSingleCellPipelineStep.Markdown finalMarkdown = new AbstractSingleCellPipelineStep.Markdown();
            finalMarkdown.headerYml = finalMarkdown.getDefaultHeader();
            finalMarkdown.chunks = new ArrayList<>();
            for (File markdown : _resumer.getMarkdownsInOrder())
            {
                finalMarkdown.chunks.add(new AbstractSingleCellPipelineStep.Chunk(null, null, null, Collections.emptyList(), "child='" + markdown.getName() + "'"));

                _resumer.getFileManager().addIntermediateFile(markdown);
            }
            finalMarkdown.chunks.add(new AbstractSingleCellPipelineStep.SessionInfoChunk());

            File finalMarkdownFile = new File(ctx.getOutputDir(), "final.rmd");
            try (PrintWriter writer = PrintWriters.getPrintWriter(finalMarkdownFile))
            {
                finalMarkdown.print(writer);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Creating Final HTML Report");
            File finalHtml = new File(ctx.getOutputDir(), "finalHtml.html");
            List<String> lines = new ArrayList<>();
            lines.add("rmarkdown::render(output_file = '" + finalHtml.getName() + "', input = '" + finalMarkdownFile.getName() + "', intermediates_dir  = '/work')");
            AbstractSingleCellPipelineStep.executeR(ctx, AbstractCellMembraneStep.CONTAINER_NAME, "pandoc", lines, null);
            _resumer.getFileManager().addIntermediateFile(finalMarkdownFile);
            _resumer.getFileManager().addIntermediateFiles(_resumer.getMarkdownsInOrder());
            _resumer.getFileManager().addIntermediateFiles(_resumer.getHtmlFilesInOrder());

            String jobDescription = StringUtils.trimToNull(ctx.getParams().optString("jobDescription"));
            for (SingleCellStep.SeuratObjectWrapper output : currentFiles)
            {
                SequenceOutputFile so = new SequenceOutputFile();
                so.setName(output.getDatasetName() == null ? output.getDatasetId() : output.getDatasetName());
                so.setCategory("Seurat Object");
                so.setFile(output.getFile());
                String description = getOutputDescription(ctx, output.getFile(), List.of("Steps: " + steps.stream().map(x -> x.getProvider().getName()).collect(Collectors.joining("; "))));
                if (jobDescription != null)
                {
                    description = jobDescription + "\n" + description;
                }
                so.setDescription(description);

                if (NumberUtils.isCreatable(output.getDatasetId()))
                {
                    try
                    {
                        Integer id = NumberUtils.createInteger(output.getDatasetId());
                        if (!inputMap.containsKey(id))
                        {
                            ctx.getLogger().warn("No input found matching dataset Id: " + output.getDatasetId());
                        }
                        else
                        {
                            SequenceOutputFile o1 = inputMap.get(id);
                            so.setLibrary_id(o1.getLibrary_id());
                            so.setReadset(o1.getReadset());
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        ctx.getLogger().error("Expected dataset ID to be an integer: " + output.getDatasetId());
                    }
                }
                else
                {
                    Set<Integer> distinctGenomes = new HashSet<>();
                    inputFiles.forEach(x -> distinctGenomes.add(x.getLibrary_id()));
                    if (distinctGenomes.size() == 1)
                    {
                        so.setLibrary_id(distinctGenomes.iterator().next());
                    }
                }

                //Note: when starting with a seurat object (not loupe), the ID is the string readset name, not an ID:
                if (so.getReadset() == null)
                {
                    if (output.getReadsetId() != null)
                    {
                        ctx.getLogger().debug("setting readset from output to: " + output.getReadsetId());
                        so.setReadset(output.getReadsetId());
                    }
                    else if (inputFiles.size() == 1)
                    {
                        ctx.getLogger().debug("only single input, so re-using readset: " + inputFiles.get(0).getReadset());
                        so.setReadset(inputFiles.get(0).getReadset());
                    }
                }

                //This indicates the job processed an input file, but did not create a new object (like running FindMarkers)
                if (originalRDSCopiedLocal.contains(output.getFile()))
                {
                    ctx.getLogger().info("Sequence output is the same as an input, will not re-create output for seurat object: " + output.getFile().getPath());
                }
                else
                {
                    Set<File> existingOutputs = _resumer.getFileManager().getOutputsToCreate().stream().map(SequenceOutputFile::getFile).collect(Collectors.toSet());
                    if (existingOutputs.contains(so.getFile()))
                    {
                        ctx.getLogger().info("The RDS file has already been registered as an output, will not re-add: " + so.getFile().getPath());
                    }
                    else
                    {
                        _resumer.getFileManager().addSequenceOutput(so);
                    }
                }

                // This could be a little confusing, but add one record out seurat output, even though there is one HTML file:
                SequenceOutputFile o = new SequenceOutputFile();
                o.setCategory("Seurat Report");
                o.setDescription(description);
                o.setFile(finalHtml);
                o.setLibrary_id(so.getLibrary_id());
                o.setReadset(so.getReadset());
                o.setName("Seurat Report: " + so.getName());

                _resumer.getFileManager().addSequenceOutput(o);
            }

            _resumer.markComplete(ctx);
        }
    }

    private List<SingleCellStep.SeuratObjectWrapper> processRawCounts(JobContext ctx, List<SequenceOutputFile> inputFiles, String basename) throws PipelineJobException
    {
        List<SingleCellStep.SeuratObjectWrapper> currentFiles;

        // Step 1: read raw data
        PrepareRawCounts prepareRawCounts = new PrepareRawCounts.Provider().create(ctx);
        ctx.getLogger().info("Starting to run: " + prepareRawCounts.getProvider().getLabel());
        if (_resumer.isStepComplete(0))
        {
            ctx.getLogger().info("resuming from saved state");
            currentFiles = _resumer.getSeuratFromStep(0);
        }
        else
        {
            RecordedAction action = new RecordedAction(prepareRawCounts.getProvider().getLabel());
            Date start = new Date();
            action.setStartTime(start);

            List<SingleCellStep.SeuratObjectWrapper> inputMatrices = new ArrayList<>();
            inputFiles.forEach(x -> {
                String datasetName = ctx.getSequenceSupport().getCachedReadset(x.getReadset()).getName();
                File countsDir = new File(x.getFile().getParentFile(), "raw_feature_bc_matrix");
                if (!countsDir.exists())
                {
                    throw new IllegalArgumentException("Unable to find file: " + countsDir.getPath());
                }

                // The outs directory:
                File source = x.getFile().getParentFile();

                try
                {
                    File localCopy = new File(ctx.getOutputDir(), x.getRowid() + "_RawData");
                    if (localCopy.exists())
                    {
                        ctx.getLogger().debug("Deleting directory: " + localCopy.getPath());
                        FileUtils.deleteDirectory(localCopy);
                    }

                    ctx.getLogger().debug("Copying raw data directory: " + source.getPath());
                    ctx.getLogger().debug("To: " + localCopy.getPath());

                    FileUtils.copyDirectory(source, localCopy);
                    _resumer.getFileManager().addIntermediateFile(localCopy);

                    inputMatrices.add(new SingleCellStep.SeuratObjectWrapper(String.valueOf(x.getRowid()), datasetName, localCopy, x));
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException(e);
                }
            });

            inputFiles.forEach(currentFile -> action.addInput(currentFile.getFile(), "Input Loupe File"));

            SingleCellStep.Output output0 = prepareRawCounts.execute(ctx, inputMatrices, basename + ".rawCounts");
            currentFiles =  output0.getSeuratObjects();
            if (currentFiles == null || currentFiles.isEmpty())
            {
                throw new PipelineJobException("No seurat objects produced by: " + prepareRawCounts.getProvider().getName());
            }
            currentFiles.stream().map(SingleCellStep.SeuratObjectWrapper::getFile).forEach(_resumer.getFileManager()::addIntermediateFile);
            _resumer.getFileManager().addIntermediateFiles(currentFiles.stream().map(x -> CellHashingServiceImpl.get().getCellBarcodesFromSeurat(x.getFile())).collect(Collectors.toSet()));
            _resumer.getFileManager().addIntermediateFiles(currentFiles.stream().map(x -> CellHashingServiceImpl.get().getMetaTableFromSeurat(x.getFile())).collect(Collectors.toSet()));

            _resumer.setStepComplete(ctx.getLogger(), prepareRawCounts, 0, action, output0.getSeuratObjects(), output0.getMarkdownFile(), output0.getHtmlFile());
        }

        return currentFiles;
    }

    protected static class Resumer extends AbstractResumer
    {
        public static final String JSON_NAME = "processSingleCellCheckpoint.json";

        private Map<Integer, File> _markdowns = new HashMap<>();
        private Map<Integer, File> _htmlFiles = new HashMap<>();
        private Map<Integer, List<SingleCellStep.SeuratObjectWrapper>> _stepOutputs = new HashMap<>();

        @Override
        protected String getJsonName()
        {
            return JSON_NAME;
        }

        //for serialization
        public Resumer()
        {

        }

        private Resumer(SequenceOutputHandler.JobContext ctx)
        {
            super(ctx.getSourceDirectory(), ctx.getLogger(), ctx.getFileManager());
        }

        public static Resumer create(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
        {
            Resumer ret;
            File json = getSerializedJson(ctx.getSourceDirectory(), JSON_NAME);
            if (!json.exists())
            {
                ret = new Resumer(ctx);
            }
            else
            {
                ret = readFromJson(json, Resumer.class);
                ret._isResume = true;
                ret.setLogger(ctx.getLogger());
                ret.setWebserverJobDir(ctx.getSourceDirectory());
                ret._fileManager.onResume(ctx.getJob(), ctx.getWorkDir());

                ctx.getLogger().debug("Cached steps: " + StringUtils.join(ret._stepOutputs.keySet(), ", "));
            }

            return ret;
        }

        public boolean isStepComplete(int stepIdx)
        {
            return _stepOutputs.containsKey(stepIdx);
        }

        public List<SingleCellStep.SeuratObjectWrapper> getSeuratFromStep(int stepIdx)
        {
            return _stepOutputs.get(stepIdx);
        }

        public void setStepComplete(Logger log, SingleCellStep step, int stepIdx, RecordedAction action, List<SingleCellStep.SeuratObjectWrapper> seurat, File markdown, File html) throws PipelineJobException
        {
            log.info("Marking step complete: " + step.getProvider().getName() + ", " + stepIdx + ", total seurat objects: " + (seurat == null ? 0 : seurat.size()));

            if (seurat != null)
            {
                seurat.forEach(x -> action.addOutput(x.getFile(), "Seurat Object", false));
            }

            action.addOutput(markdown, "Markdown File", true);
            action.addOutput(html, "HTML Report", false);

            if (_stepOutputs.containsKey(stepIdx))
            {
                log.debug("Step already marked as done: " + step.getProvider().getName() + " / " +stepIdx);
            }

            _stepOutputs.put(stepIdx, seurat);
            _markdowns.put(stepIdx, markdown);
            _htmlFiles.put(stepIdx, html);

            _recordedActions.add(action);
            saveState();
        }

        public void setMarkdowns(Map<Integer, File> markdowns)
        {
            _markdowns = markdowns;
        }

        public void setHtmlFiles(Map<Integer, File> htmlFiles)
        {
            _htmlFiles = htmlFiles;
        }

        public Map<Integer, File> getMarkdowns()
        {
            return _markdowns;
        }

        public Map<Integer, File> getHtmlFiles()
        {
            return _htmlFiles;
        }

        public List<File> getMarkdownsInOrder()
        {
            return _markdowns.keySet().stream().sorted().map(_markdowns::get).collect(Collectors.toList());
        }

        public List<File> getHtmlFilesInOrder()
        {
            return _htmlFiles.keySet().stream().sorted().map(_htmlFiles::get).collect(Collectors.toList());
        }

        public Map<Integer, List<SingleCellStep.SeuratObjectWrapper>> getStepOutputs()
        {
            return _stepOutputs;
        }

        public void setStepOutputs(Map<Integer, List<SingleCellStep.SeuratObjectWrapper>> stepOutputs)
        {
            _stepOutputs = stepOutputs;
        }
    }

    public static class TestCase extends Assert
    {
        private static final Logger _log = LogManager.getLogger(AbstractSingleCellHandler.TestCase.class);

        @Test
        public void serializeTest() throws Exception
        {
            AbstractSingleCellHandler.Resumer r = new AbstractSingleCellHandler.Resumer();
            r.setLogger(_log);
            r.setRecordedActions(new LinkedHashSet<>());
            r.setFileManager(SequencePipelineService.get().getTaskFileManager());
            RecordedAction action1 = new RecordedAction();
            action1.setName("Action1");
            action1.setDescription("Description");
            action1.addInput(new File("/input"), "Input");
            action1.addOutput(new File("/output"), "Output", false);
            r.getRecordedActions().add(action1);

            r._markdowns.put(1, new File("file1"));
            r._markdowns.put(2, new File("file2"));

            SequenceOutputFile so1 = new SequenceOutputFile();
            so1.setRowid(999);
            r._stepOutputs.put(1, List.of(new SingleCellStep.SeuratObjectWrapper("datasetId", "datasetName", new File("seurat.rds"), so1)));

            File tmp = new File(System.getProperty("java.io.tmpdir"));
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(tmp, AbstractSingleCellHandler.Resumer.JSON_NAME));

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("so1");
            so.setFile(f);
            r.getFileManager().addSequenceOutput(so);

            r.writeToJson(tmp);

            //after deserialization the RecordedAction should match the original
            AbstractSingleCellHandler.Resumer r2 = AbstractSingleCellHandler.Resumer.readFromJson(f, AbstractSingleCellHandler.Resumer.class);
            assertEquals(1, r2.getRecordedActions().size());
            RecordedAction action2 = r2.getRecordedActions().iterator().next();
            assertEquals("Action1", action2.getName());
            assertEquals("Description", action2.getDescription());
            assertEquals(1, action2.getInputs().size());
            assertEquals(new File("/input").toURI(), action1.getInputs().iterator().next().getURI());
            assertEquals(1, action2.getOutputs().size());
            assertEquals(new File("/output").toURI(), action2.getOutputs().iterator().next().getURI());
            assertEquals(1, r2.getFileManager().getOutputsToCreate().size());
            assertEquals("so1", r2.getFileManager().getOutputsToCreate().iterator().next().getName());
            assertEquals(f, r2.getFileManager().getOutputsToCreate().iterator().next().getFile());

            assertEquals("file1", r2.getMarkdowns().get(1).getName());
            assertEquals("file2", r2.getMarkdowns().get(2).getName());

            assertEquals(1, r2.getStepOutputs().size());
            assertEquals("datasetId", r2.getStepOutputs().get(1).get(0).getDatasetId());
            assertEquals("datasetName", r2.getStepOutputs().get(1).get(0).getDatasetName());
            assertEquals("seurat.rds", r2.getStepOutputs().get(1).get(0).getFile().getName());
            assertEquals(Integer.valueOf(999), r2.getStepOutputs().get(1).get(0).getSequenceOutputFileId());
            assertNull(r2.getStepOutputs().get(1).get(0).getSequenceOutputFile());

            f.delete();
        }
    }

    public static String getOutputDescription(JobContext ctx, File seuratObj, @Nullable List<String> descriptions) throws PipelineJobException
    {
        if (descriptions == null)
        {
            descriptions = new ArrayList<>();
        }
        else
        {
            //ensure mutable:
            descriptions = new ArrayList<>(descriptions);
        }

        File metaTable = CellHashingServiceImpl.get().getMetaTableFromSeurat(seuratObj, false);
        if (metaTable != null)
        {
            try (CSVReader reader = new CSVReader(Readers.getReader(metaTable), ','))
            {
                String[] line;

                int totalCells = 0;
                int totalSinglet = 0;
                int totalDiscordant = 0;
                int lowOrNegative = 0;
                int totalDoublet = 0;
                double totalSaturation = 0.0;

                int hashingIdx = -1;
                int saturationIdx = -1;
                boolean hashingUsed = true;
                while ((line = reader.readNext()) != null)
                {
                    // This will test whether this is the first line or not
                    if (hashingIdx == -1)
                    {
                        hashingIdx = Arrays.asList(line).indexOf("HTO.Classification");
                        if (hashingIdx == -1)
                        {
                            ctx.getLogger().debug("HTO.Classification field not present, skipping");
                            hashingUsed = false;
                            hashingIdx = -2;
                        }

                        saturationIdx = Arrays.asList(line).indexOf("Saturation.RNA");
                    }
                    else
                    {
                        totalCells++;
                        if (hashingUsed && hashingIdx >= 0)
                        {
                            String val = line[hashingIdx];
                            if ("Singlet".equals(val))
                            {
                                totalSinglet++;
                            }
                            else if ("Doublet".equals(val))
                            {
                                totalDoublet++;
                            }
                            else if ("Discordant".equals(val))
                            {
                                totalDiscordant++;
                            }
                            else if ("Low Counts".equalsIgnoreCase(val) || "Negative".equals(val) || "ND".equalsIgnoreCase(val))
                            {
                                lowOrNegative++;
                            }
                            else if ("NotUsed".equals(val))
                            {
                                hashingUsed = false;
                            }
                        }

                        if (saturationIdx >= 0)
                        {
                            double saturation = Double.parseDouble(line[saturationIdx]);
                            totalSaturation += saturation;
                        }
                    }
                }

                NumberFormat pf = NumberFormat.getPercentInstance();
                pf.setMaximumFractionDigits(2);

                NumberFormat decimal = DecimalFormat.getNumberInstance();
                decimal.setGroupingUsed(false);

                descriptions.add("Total Cells: " + decimal.format(totalCells));
                if (hashingUsed)
                {
                    descriptions.add("Total Singlet: " + decimal.format(totalSinglet));
                    descriptions.add("% Singlet: " + pf.format((double) totalSinglet / (double) totalCells));
                    descriptions.add("% Doublet: " + pf.format((double) totalDoublet / (double) totalCells));
                    descriptions.add("% Discordant: " + pf.format((double) totalDiscordant / (double) totalCells));
                    descriptions.add("% Negative or Low Count: " + pf.format((double) lowOrNegative / (double) totalCells));
                }
                else
                {
                    descriptions.add("Hashing not used");
                }

                if (totalSaturation > 0)
                {
                    descriptions.add("Mean RNA Saturation: " + (totalSaturation / (double) totalCells));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        if (ctx.getParams().optBoolean("singleCellRawData.PrepareRawCounts.useSoupX", false))
        {
            descriptions.add("SoupX: true");
        }

        String hashingMethods = ctx.getParams().optString("singleCell.RunCellHashing.consensusMethods");
        if (StringUtils.trimToNull(hashingMethods) != null)
        {
            descriptions.add("Hashing: " + hashingMethods);
        }

        String citeNormalize = ctx.getParams().optString("singleCell.AppendCiteSeq.normalizeMethod");
        if (StringUtils.trimToNull(citeNormalize) != null)
        {
            descriptions.add("Cite-seq Normalization: " + citeNormalize);
        }

        if (ctx.getParams().optBoolean("singleCell.AppendCiteSeq.runCellBender", false))
        {
            descriptions.add("Cite-seq/CellBender: true");
        }

        return StringUtils.join(descriptions, "\n");
    }
}
