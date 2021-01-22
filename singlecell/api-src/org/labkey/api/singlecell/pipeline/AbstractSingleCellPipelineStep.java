package org.labkey.api.singlecell.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import com.google.common.io.Files;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

abstract public class AbstractSingleCellPipelineStep extends AbstractPipelineStep implements SingleCellStep
{
    public AbstractSingleCellPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        SingleCellOutput output = new SingleCellOutput();

        File rmd = createRmd(ctx, inputObjects, outputPrefix);
        executeR(ctx, rmd, outputPrefix);

        File markdownFile = getExpectedMarkdownFile(ctx, outputPrefix);
        if (!markdownFile.exists())
        {
            //TODO: this is for testing only:
            try
            {
                Files.touch(markdownFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            //throw new PipelineJobException("Unable to find markdown file: " + markdownFile.getPath());
        }
        output.setMarkdownFile(markdownFile);

        File htmlFile = getExpectedHtmlFile(ctx, outputPrefix);
        if (!htmlFile.exists())
        {
            //TODO: this is for testing only:
            try
            {
                Files.touch(markdownFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            //throw new PipelineJobException("Unable to find HTML file: " + htmlFile.getPath());
        }
        output.setHtmlFile(htmlFile);

        List<SeuratObjectWrapper> outputs = new ArrayList<>();
        File tracker = new File(ctx.getOutputDir(), "savedSeuratObjects.txt");
        try (CSVReader reader = new CSVReader(Readers.getReader(tracker), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                File f = new File(ctx.getOutputDir(), line[1]);
                if (!f.exists())
                {
                    //TODO: this is for testing only:
                    try
                    {
                        Files.touch(markdownFile);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                    //throw new PipelineJobException("File not found: " + f.getPath());
                }

                outputs.add(new SeuratObjectWrapper(line[0], line[1], f));
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

        output.setHtmlFile(getExpectedHtmlFile(ctx, outputPrefix));
        output.setMarkdownFile(getExpectedMarkdownFile(ctx, outputPrefix));

        return output;
    }

    private File getExpectedMarkdownFile(SequenceOutputHandler.JobContext ctx, String outputPrefix)
    {
        return new File(ctx.getOutputDir(), outputPrefix + ".md");
    }

    private File getExpectedHtmlFile(SequenceOutputHandler.JobContext ctx, String outputPrefix)
    {
        return new File(ctx.getOutputDir(), outputPrefix + ".html");
    }

    protected File createRmd(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        File outfile = new File(ctx.getOutputDir(), outputPrefix + ".rmd");
        try (PrintWriter out = PrintWriters.getPrintWriter(outfile))
        {
            Markdown markdown = new Markdown();
            markdown.headerYml = getDefaultHeader();
            markdown.setup = new SetupChunk(getRLibraries());
            markdown.chunks = getChunks(inputObjects);
            markdown.chunks.add(createFinalChunk());

            markdown.print(out);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outfile;
    }

    protected List<String> getDefaultHeader()
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
        ret.add("    cache: false");
        ret.add("    df_print: paged");

        return ret;
    }

    protected List<Chunk> getChunks(List<SeuratObjectWrapper> inputObjects) throws PipelineJobException
    {
        List<Chunk> ret = new ArrayList<>();
        ret.add(createParamChunk(inputObjects));
        ret.add(new Chunk(getProvider().getName(), getProvider().getLabel(), null, loadChunkFromFile()));

        return ret;
    }

    @Override
    public boolean requiresHashingOrCiteSeq()
    {
        return false;
    }

    public static class Markdown
    {
        List<Chunk> chunks;
        Chunk setup;
        List<String> headerYml;

        public void print(PrintWriter out)
        {
            out.println("---");
            headerYml.forEach(out::println);
            out.println("---");
            out.println("");

            chunks.forEach(chunk -> chunk.print(out));
        }
    }

    protected void executeR(SequenceOutputHandler.JobContext ctx, File rmd, String outputPrefix) throws PipelineJobException
    {
        File localRScript = new File(ctx.getOutputDir(), outputPrefix + ".R");
        if (!localRScript.exists())
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(localRScript))
            {
                writer.println("rmarkdown::render('/work/" + rmd.getName() + "')");
                writer.println("print('Rmarkdown complete')");

            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            ctx.getLogger().info("script exists, re-using: " + localRScript.getPath());
        }

        File localBashScript = new File(ctx.getOutputDir(), "wrapper.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#/bin/bash");
            writer.println("set -e");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull ghcr.io/bimberlab/cellhashr:latest");
            writer.println("sudo $DOCKER run --rm=true \\");
            if (SequencePipelineService.get().getMaxRam() != null)
            {
                writer.println("--memory=" + SequencePipelineService.get().getMaxRam() + "g \\");
                writer.println("-e SEQUENCEANALYSIS_MAX_RAM \\");
            }

            if (SequencePipelineService.get().getMaxThreads(ctx.getLogger()) != null)
            {
                writer.println("-e SEQUENCEANALYSIS_MAX_THREADS \\");
            }

            writer.println("-v \"${WD}:/work\" \\");
            writer.println("-v \"${HOME}:/homeDir\" \\");
            writer.println("-u $UID \\");
            writer.println("-e USERID=$UID \\");
            writer.println("-w /work \\");
            writer.println("-e HOME=/homeDir \\");
            writer.println(getDockerContainerName() + " \\");
            writer.println("Rscript --vanilla " + localRScript.getName());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(ctx.getLogger());
        rWrapper.setWorkingDir(ctx.getOutputDir());
        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));
    }

    protected String prepareValueForR(ToolParameterDescriptor pd)
    {
        String val = StringUtils.trimToNull(pd.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        if (val == null)
        {
            return "NULL";
        }
        else if (NumberUtils.isCreatable(val))
        {
            return val;
        }
        else if ("sequenceanalysis-trimmingtextarea".equals(pd.getFieldXtype()))
        {
            String[] vals = val.split(",");
            return "c('" + StringUtils.join(vals, "','") + "')";
        }

        return  "'" + val + "'";
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

    protected Chunk createParamChunk(List<SeuratObjectWrapper> inputObjects)
    {
        List<String> body = new ArrayList<>();

        for (ToolParameterDescriptor pd : getProvider().getParameters())
        {
            body.add(pd.getName() + " <- " + prepareValueForR(pd));
        }
        body.add("");

        //Read RDS:
        body.add("seuratObjects <- list()");
        body.add("datasetIdToName <- list()");
        for (SeuratObjectWrapper so : inputObjects)
        {
            body.add("seuratObjects[['" + so.getDatasetId() + "']] <- readRDS(file = '" + so.getFile().getName() + "')");
            body.add("datasetIdToName[['" + so.getDatasetId() + "']] <- '" + so.getDatasetName() + "'");
        }

        body.add("# This will store any modified/transformed Seurat objects:");
        body.add("newSeuratObjects <- list()");

        body.add("intermediateFiles <- c()");
        body.add("addIntermediateFile <- function(f) { intermediateFiles <<- c(intermediateFiles, f) } ");

        return new Chunk("parameters", null, null, body);
    }

    protected Chunk createFinalChunk()
    {
        List<String> body = new ArrayList<>();

        return new Chunk("saveData", null, null, body);
    }

    public static class Chunk
    {
        String header;
        String extraText;
        String chunkName;
        String chunkOpts;
        List<String> bodyLines;

        public Chunk(String chunkName, @Nullable String header, @Nullable String extraText, List<String> bodyLines)
        {
            this.chunkName = chunkName;
            this.extraText = extraText;
            this.header = header;
            this.bodyLines = new ArrayList<>(bodyLines);
        }

        public void print(PrintWriter out)
        {
            out.println("");
            if (header != null)
            {
                out.println("##" + header);
            }

            if (extraText != null)
            {
                out.println("");
                out.println(extraText);
            }
            out.println("");
            out.println("```{r " + chunkName + (chunkOpts == null ? "" : ", " + chunkOpts) + "}");
            bodyLines.forEach(out::println);
            out.println("");
            out.println("```");
        }
    }

    public static class SetupChunk extends Chunk
    {
        public SetupChunk(Collection<String> libraries)
        {
            super("setup", null, null, libraries.stream().map(x -> {
                return "library(" + x + ")";
            }).collect(Collectors.toList()));

            bodyLines.add("knitr::opts_chunk$set(echo = TRUE)");
            chunkOpts = "include=FALSE";
        }
    }
}
