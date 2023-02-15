package org.labkey.api.singlecell.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

abstract public class AbstractSingleCellPipelineStep extends AbstractPipelineStep implements SingleCellStep
{
    public static final String SEURAT_THREADS = "seuratMaxThreads";

    public AbstractSingleCellPipelineStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        SingleCellOutput output = new SingleCellOutput();

        File tracker = new File(ctx.getOutputDir(), "savedSeuratObjects.txt");
        if (tracker.exists())
        {
            ctx.getLogger().debug("deleting pre-existing file: " + tracker.getName());
            tracker.delete();
        }

        File rmd = createRmd(output, ctx, inputObjects, outputPrefix);
        if (hasCompleted())
        {
            ctx.getLogger().info("Step has already completed, skipping R");
        }
        else
        {
            executeR(ctx, rmd, outputPrefix);
        }

        ctx.getFileManager().addIntermediateFile(rmd);
        ctx.getFileManager().addIntermediateFile(new File(rmd.getParentFile(), FileUtil.getBaseName(rmd.getName()) + "_files"));

        File markdownFile = getExpectedMarkdownFile(ctx, outputPrefix);
        if (!markdownFile.exists())
        {
            throw new PipelineJobException("Unable to find markdown file: " + markdownFile.getPath());
        }
        output.setMarkdownFile(markdownFile);

        File htmlFile = getExpectedHtmlFile(ctx, outputPrefix);
        if (!htmlFile.exists())
        {
            throw new PipelineJobException("Unable to find HTML file: " + htmlFile.getPath());
        }
        output.setHtmlFile(htmlFile);

        List<SeuratObjectWrapper> outputs = new ArrayList<>();
        if (!tracker.exists())
        {
            throw new PipelineJobException("File not found: " + tracker.getPath());
        }

        try (CSVReader reader = new CSVReader(Readers.getReader(tracker), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                File f = new File(ctx.getOutputDir(), line[2]);
                if (!f.exists())
                {
                    throw new PipelineJobException("File not found: " + f.getPath());
                }

                getPipelineCtx().getLogger().debug("Output seurat: " + line[0] + " / " + line[1] + " / "+ f.getName() + " / " + line[3] + " / " + line[4]);

                String outputIdVal = "NA".equals(line[3]) ? null : StringUtils.trimToNull(line[3]);
                if (outputIdVal != null && !NumberUtils.isCreatable(outputIdVal))
                {
                    throw new PipelineJobException("Unable to parse outputFileId: " + outputIdVal);
                }

                String readsetIdVal = "NA".equals(line[4]) ? null : StringUtils.trimToNull(line[4]);
                if (readsetIdVal != null && !NumberUtils.isCreatable(readsetIdVal))
                {
                    throw new PipelineJobException("Unable to parse readsetId: " + readsetIdVal);
                }

                outputs.add(new SeuratObjectWrapper(line[0], line[1], f, outputIdVal == null ? null : Integer.parseInt(outputIdVal), readsetIdVal == null ? null : Integer.parseInt(readsetIdVal)));
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        tracker.delete();

        File intermediates = new File(ctx.getOutputDir(), "intermediateFiles.txt");
        if (intermediates.exists())
        {
            getPipelineCtx().getLogger().debug("inspecting intermediateFiles.txt");
            try (CSVReader reader = new CSVReader(Readers.getReader(intermediates), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    File f = new File(ctx.getOutputDir(), line[1]);
                    if (f.exists())
                    {
                        output.addIntermediateFile(f);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            intermediates.delete();
        }

        if (!outputs.isEmpty())
        {
            output.setSeuratObjects(outputs);
        }

        return output;
    }

    protected File getExpectedMarkdownFile(SequenceOutputHandler.JobContext ctx, String outputPrefix)
    {
        return new File(ctx.getOutputDir(), outputPrefix + ".md");
    }

    private File getExpectedHtmlFile(SequenceOutputHandler.JobContext ctx, String outputPrefix)
    {
        return new File(ctx.getOutputDir(), outputPrefix + ".html");
    }

    protected File createRmd(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        File outfile = new File(ctx.getOutputDir(), outputPrefix + ".rmd");
        try (PrintWriter out = PrintWriters.getPrintWriter(outfile))
        {
            Markdown markdown = new Markdown();
            markdown.headerYml = markdown.getDefaultHeader();
            markdown.setup = new SetupChunk(getRLibraries());
            markdown.chunks = new ArrayList<>();
            markdown.chunks.add(createParamChunk(ctx, inputObjects, outputPrefix));
            markdown.chunks.addAll(getChunks(ctx));
            markdown.chunks.add(createFinalChunk());

            markdown.print(out);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outfile;
    }

    protected List<Chunk> getChunks(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        List<Chunk> ret = new ArrayList<>();
        ret.add(new Chunk(getProvider().getName(), getProvider().getLabel(), null, loadChunkFromFile()));

        return ret;
    }

    @Override
    public boolean requiresHashing(SequenceOutputHandler.JobContext ctx)
    {
        return false;
    }

    @Override
    public boolean requiresCiteSeq(SequenceOutputHandler.JobContext ctx)
    {
        return false;
    }

    public static class Markdown
    {
        public List<Chunk> chunks;
        public Chunk setup = null;
        public List<String> headerYml;

        public void print(PrintWriter out)
        {
            out.println("---");
            headerYml.forEach(out::println);
            out.println("---");
            out.println("");
            if (setup != null)
            {
                setup.print(out);
            }
            chunks.forEach(chunk -> chunk.print(out));
        }

        public List<String> getDefaultHeader()
        {
            List<String> ret = new ArrayList<>();

            ret.add("title: \"Single Cell Report\"");
            ret.add("date: \"`r Sys.Date()`\"");
            ret.add("output:");
            ret.add("  rmdformats::html_clean:");
            ret.add("    highlight: kate");
            ret.add("    self_contained: true");
            ret.add("    thumbnails: true");
            ret.add("    fig_width: 12");
            ret.add("    code_folding: hide");
            ret.add("    keep_md: true");
            ret.add("    gallery: true");
            ret.add("    lightbox: true");
            ret.add("    toc_depth: 3");
            ret.add("    cache: false");
            ret.add("    df_print: paged");

            return ret;
        }
    }

    protected boolean hasCompleted()
    {
        return false;
    }

    protected void executeR(SequenceOutputHandler.JobContext ctx, File rmd, String outputPrefix) throws PipelineJobException
    {
        List<String> lines = new ArrayList<>();
        lines.add("rmarkdown::render(output_file = '" + getExpectedHtmlFile(ctx, outputPrefix).getName() + "', input = '" + rmd.getName() + "', intermediates_dir  = '/work')");
        lines.add("print('Rmarkdown complete')");
        lines.add("");

        File errorFile = getSeuratErrorFile(ctx);
        if (errorFile.exists())
        {
            errorFile.delete();
        }

        Integer seuratThreads = null;
        if (getProvider().getParameterByName(SEURAT_THREADS) != null)
        {
            seuratThreads = getProvider().getParameterByName(SEURAT_THREADS).extractValue(ctx.getJob(), getProvider(), getStepIdx(), Integer.class, null);
        }

        executeR(ctx, getDockerContainerName(), outputPrefix, lines, seuratThreads);

        handlePossibleFailure(ctx, outputPrefix);
    }

    protected static SeuratToolParameter getSeuratThreadsParam()
    {
        return SeuratToolParameter.create(SEURAT_THREADS, "Max Threads", "If provided, the docker session will set future::plan(strategy='multisession', workers=XXX), which is supported by certain Seurat functions.", "ldk-integerfield", new JSONObject(){{
            put("minValue", 0);
        }}, null);
    }

    public static void executeR(SequenceOutputHandler.JobContext ctx, String dockerContainerName, String outputPrefix, List<String> lines, @Nullable Integer seuratThreads) throws PipelineJobException
    {
        File localRScript = new File(ctx.getOutputDir(), FileUtil.makeLegalName(outputPrefix + ".R").replaceAll(" ", "_"));
        try (PrintWriter writer = PrintWriters.getPrintWriter(localRScript))
        {
            lines.forEach(writer::println);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File localBashScript = new File(ctx.getOutputDir(), "dockerWrapper.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull " + dockerContainerName);
            writer.println("sudo $DOCKER run --rm=true \\");

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_THREADS=" + maxThreads + " \\");
            }

            if (seuratThreads != null)
            {
                if (maxThreads != null && maxThreads < seuratThreads)
                {
                    seuratThreads = maxThreads;
                }

                writer.println("\t-e SEURAT_MAX_THREADS=" + seuratThreads + " \\");
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                //int swap = 4*maxRam;
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            File tmpDir = new File(SequencePipelineService.get().getJavaTempDir());
            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-v \"" + tmpDir.getPath() + ":/tmp\" \\");
            writer.println("\t-v \"${HOME}:/homeDir\" \\");
            writer.println("\t-u $UID \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t-e TMPDIR=/tmp \\");
            writer.println("\t-w /work \\");
            //NOTE: this seems to disrupt packages installed into home
            //writer.println("\t-e HOME=/homeDir \\");
            writer.println("\t" + dockerContainerName + " \\");
            writer.println("\tRscript --vanilla '" + localRScript.getName() + "'");
            writer.println("EXIT_CODE=$?");
            writer.println("echo 'Bash script complete: '$EXIT_CODE");
            writer.println("exit $EXIT_CODE");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(ctx.getLogger());
        rWrapper.setWorkingDir(ctx.getOutputDir());
        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));

        localRScript.delete();
        localBashScript.delete();
    }

    protected void addParameterVariables(SeuratToolParameter pd, List<String> body)
    {
        String val = StringUtils.trimToNull(pd.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        if (val == null)
        {
            body.add((pd.getVariableName() + " <- NULL"));
        }
        else if ("false".equals(val))
        {
            body.add((pd.getVariableName() + " <- FALSE"));
        }
        else if ("true".equals(val))
        {
            body.add((pd.getVariableName() + " <- TRUE"));
        }
        else if (NumberUtils.isCreatable(val))
        {
            body.add((pd.getVariableName() + " <- " + val));
        }
        else if ("sequenceanalysis-trimmingtextarea".equals(pd.getFieldXtype()))
        {
            val = val.replace("'", "\\\'");
            serializeMultiValueParam(pd, body, val);
        }
        else if (pd.isMultiValue())
        {
            serializeMultiValueParam(pd, body, val);
        }
        else
        {
            body.add((pd.getVariableName() + " <- '" + val + "'"));
        }
    }

    private void serializeMultiValueParam(SeuratToolParameter pd, List<String> body, String val)
    {
        String[] vals = val.split(pd.getDelimiter());
        final int batchSize = 75;
        int numBatches = (int)Math.ceil((double)vals.length / batchSize);

        for (int i=0;i<numBatches;i++)
        {
            int start = (i * batchSize);
            int end = Math.min(start + batchSize, vals.length);
            body.add(pd.getVariableName() + " <- " + "c(" + (i == 0 ? "" : pd.getVariableName() + ", ") + "'" + StringUtils.join(Arrays.copyOfRange(vals, start, end), "','") + "')");
        }
    }

    protected List<String> loadChunkFromFile() throws PipelineJobException
    {
        return loadChunkFromFile("singlecell", "chunks/" + getProvider().getName() + ".R");
    }

    protected List<String> loadChunkFromFile(String moduleName, String path) throws PipelineJobException
    {
        File rFile = new File(SequenceAnalysisService.get().getScriptPath(moduleName, path));
        if (!rFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + path);
        }

        List<String> ret = new ArrayList<>();
        try (BufferedReader reader = Readers.getReader(rFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                ret.add(line);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return ret;
    }

    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        List<String> body = new ArrayList<>();

        for (ToolParameterDescriptor pd : getProvider().getParameters())
        {
            if (pd instanceof SeuratToolParameter)
            {
                SeuratToolParameter stp = (SeuratToolParameter)pd;
                if (stp.shouldIncludeInMarkdown(getPipelineCtx().getJob(), getProvider(), getStepIdx()))
                {
                    addParameterVariables(stp, body);
                }
            }
        }
        body.add("");
        body.add("outputPrefix <- '" + outputPrefix + "'");

        //Read RDS:
        body.add("");
        body.add("seuratObjects <- list()");
        body.add("datasetIdToName <- list()");
        body.add("datasetIdToOutputFileId<- list()");
        body.add("datasetIdToReadset<- list()");
        for (SeuratObjectWrapper so : inputObjects)
        {
            body.add("seuratObjects[['" + so.getDatasetId() + "']] <- " + printInputFile(so));
            body.add("datasetIdToName[['" + so.getDatasetId() + "']] <- '" + so.getDatasetName() + "'");

            if (so.getSequenceOutputFileId() != null)
            {
                body.add("datasetIdToOutputFileId[['" + so.getDatasetId() + "']] <- " + so.getSequenceOutputFileId());
            }

            if (so.getReadsetId() != null)
            {
                body.add("datasetIdToReadset[['" + so.getDatasetId() + "']] <- " + so.getReadsetId());
            }
        }

        body.addAll(loadChunkFromFile("singlecell", "chunks/Functions.R"));

        return new Chunk("parameters", null, null, body);
    }

    protected String printInputFile(SeuratObjectWrapper so)
    {
        return "'" + so.getFile().getName() + "'";
    }

    protected Chunk createFinalChunk() throws PipelineJobException
    {
        List<String> body = loadChunkFromFile("singlecell", "chunks/SaveData.R");

        return new Chunk("saveData", null, null, body);
    }

    public static class Chunk
    {
        public String header;
        public String extraText;
        public String chunkName;
        public String chunkOpts;
        public List<String> bodyLines;

        public Chunk(String chunkName, @Nullable String header, @Nullable String extraText, List<String> bodyLines)
        {
            this(chunkName, header, extraText, bodyLines, null);
        }

        public Chunk(String chunkName, @Nullable String header, @Nullable String extraText, List<String> bodyLines, String chunkOpts)
        {
            this.chunkName = chunkName;
            this.extraText = extraText;
            this.header = header;
            this.bodyLines = new ArrayList<>(bodyLines);
            this.chunkOpts = chunkOpts;
        }

        public void print(PrintWriter out)
        {
            out.println("");
            if (header != null)
            {
                out.println("## " + header);
            }

            if (extraText != null)
            {
                out.println("");
                out.println(extraText);
            }

            out.println("");
            if (chunkOpts != null || chunkName != null || !bodyLines.isEmpty())
            {
                out.println("```{r " + (chunkName == null ? "" : chunkName) + (chunkOpts == null ? "" : ", " + chunkOpts) + "}");
                bodyLines.forEach(out::println);
                out.println("");
                out.println("```");
            }
        }
    }

    public static class SetupChunk extends Chunk
    {
        public SetupChunk(Collection<String> libraries)
        {
            super("setup", null, null, libraries.stream().map(x -> {
                return "library(" + x + ")";
            }).collect(Collectors.toList()));

            bodyLines.add("knitr::opts_chunk$set(echo = TRUE, message=FALSE, warning=FALSE, error=FALSE)");
            chunkOpts = "include=FALSE";
        }
    }

    public static class SessionInfoChunk extends Chunk
    {
        public SessionInfoChunk()
        {
            super("sessionInfo", "Session Info", null, new ArrayList<>());
            bodyLines.add("sessionInfo()");
        }
    }

    @Override
    public boolean isIncluded(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputs) throws PipelineJobException
    {
        return true;
    }

    protected static File getSeuratErrorFile(SequenceOutputHandler.JobContext ctx)
    {
        return new File(ctx.getOutputDir(), "seuratErrors.txt");
    }

    protected void onFailure(SequenceOutputHandler.JobContext ctx, String outputPrefix) throws PipelineJobException
    {
        // This allows subclasses to implement tool-specific failure handling
    }

    private void handlePossibleFailure(SequenceOutputHandler.JobContext ctx, String outputPrefix) throws PipelineJobException
    {
        File errorFile = getSeuratErrorFile(ctx);
        if (errorFile.exists())
        {
            try
            {
                List<String> errors = IOUtils.readLines(Readers.getReader(errorFile));
                errorFile.delete();

                onFailure(ctx, outputPrefix);

                File html = getExpectedHtmlFile(ctx, outputPrefix);
                if (html.exists())
                {
                    ctx.getLogger().info("Copying HTML locally for debugging: " + html.getName());
                    File target = new File(ctx.getSourceDirectory(), html.getName());
                    if (target.exists())
                    {
                        target.delete();
                    }

                    Files.copy(html.toPath(), target.toPath());
                }
                else
                {
                    ctx.getLogger().info("HTML not found: " + html.getPath());
                }

                ctx.getJob().setStatus(PipelineJob.TaskStatus.error, " Errors: " + StringUtils.join(errors, ";"));
                throw new PipelineJobException(getProvider().getName() + " Errors: " + StringUtils.join(errors, ";"));
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
