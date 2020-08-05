/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * User: bbimber
 * Date: 4/23/12
 * Time: 2:02 PM
 */

// Handles the basics of testing the Fastqc configuration, creating the fastqc command line, and invoking fastqc. This code
// is patterned after DotRunner.
public class FastqcRunner
{
    private Logger _logger;
    private int _threads = 1;
    private boolean _cacheResults = true;

    public FastqcRunner(@Nullable Logger log)
    {
        if (log == null)
        {
            _logger = LogManager.getLogger(FastqcRunner.class);
        }
        else
        {
            _logger = log;
        }
    }

    public void setCacheResults(boolean cacheResults)
    {
        _cacheResults = cacheResults;
    }

    public String execute(List<File> sequenceFiles, @Nullable Map<File, String> fileLabels) throws IOException
    {
        //remove duplicates
        List<File> uniqueFiles = new ArrayList<>();
        for (File f : sequenceFiles)
        {
            if (!uniqueFiles.contains(f))
                uniqueFiles.add(f);
        }

        Set<File> filesCreated = new HashSet<>();

        for (File f : uniqueFiles)
        {
            //first see if we have cached HTML, otherwise run
            File expectedHtml = getExpectedHtmlFile(f);
            File zip = getExpectedZipFile(f);
            if (!expectedHtml.exists() || !zip.exists())
            {
                runForFile(f);

                if (zip.exists())
                {
                    _logger.info("adding ZIP: " + zip.getPath());
                    filesCreated.add(zip);
                }
                else
                {
                    _logger.error("ZIP file not found, expected: " + zip.getPath());
                }

                //force compression
                getExpectedHtmlFile(f);

                filesCreated.add(expectedHtml);
            }
        }

        return processOutput(uniqueFiles, filesCreated, fileLabels);
    }

    private void runForFile(File f)
    {
        try
        {
            List<String> params = getParams(f);
            params.add(f.getAbsolutePath());

            _logger.info("running fastqc:");
            _logger.info(StringUtils.join(params, " "));
            ProcessBuilder pb = new ProcessBuilder(params);
            pb.redirectErrorStream(true);
            pb.directory(f.getParentFile());

            Process p = pb.start();
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream(), StringUtilsLabKey.DEFAULT_CHARSET)))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    _logger.info(line);
                }

                int returnCode = p.waitFor();

                if (returnCode != 0)
                {
                    throw new IOException("FastQC failed with error code " + returnCode);
                }
            }
            catch (Exception e)
            {
                _logger.error(e.getMessage(), e);
                throw new RuntimeException("Failed writing output for process in '" + (pb != null && pb.directory() != null ? pb.directory().getPath() : "") + "'.", e);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getExpectedBasename(File f)
    {
        String basename = FileUtil.getBaseName(f);
        FileType gz = new FileType(".gz");
        if (gz.isType(f))
        {
            basename = FileUtil.getBaseName(basename);
        }

        if (f.getName().endsWith(".fq.gz") || f.getName().endsWith(".fq"))
        {
            basename = basename + ".fq";
        }

        return basename;
    }

    public File getExpectedZipFile(File f)
    {
        File expectedHtml = getExpectedHtmlFile(f);

        return new File(expectedHtml.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(expectedHtml)) + ".zip");
    }

    public File getExpectedHtmlFile(File f)
    {
        File uncompressed = new File(f.getParentFile().getAbsolutePath(), getExpectedBasename(f) + "_fastqc.html");

        //to handle legacy installs with existing uncompressed files
        File compressed = new File(uncompressed.getPath() + ".gz");
        if (uncompressed.exists())
        {
            _logger.info("compressing existing file: " + uncompressed.getPath());
            Compress.compressGzip(uncompressed, compressed);
            uncompressed.delete();
        }

        return compressed;
    }

    private String processOutput(List<File> inputFiles, Set<File> filesCreated, @Nullable Map<File, String> fileLabels)
    {
        //NOTE: this allows remote servers to run/cache the data.  AppProps.getContextPath() will fail remotely, so abort.
        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            return "";
        }

        String output = "";
        String header = "<div class=\"fastqc_overview\"><h2>File Summary:</h2><ul>";

        try
        {
            String delim = "";
            String css = AppProps.getInstance().getContextPath() + "/SequenceAnalysis/fastqc.css";
            int counter = 0;
            for (File f : inputFiles)
            {
                File htmlFile = getExpectedHtmlFile(f);
                if (!htmlFile.exists())
                {
                    output += "<p>Unable to find output for file: " + f.getName() + "</p>";
                    continue;
                }

                String html = readCompressedHtmlReport(htmlFile);

                //add an outer DIV so we can apply styles only to the report
                html = html.replaceAll("<body>", "<div class=\"fastqc\">");
                html = html.replaceAll("</body>", "</div>");


                //update IDs so links will point to correct file after concatenated:
                String suffix = f.getName().replaceAll("\\.", "_");
                String title;
                if (fileLabels == null || !fileLabels.containsKey(f))
                {
                    title = f.getName();
                }
                else
                {
                    title = fileLabels.get(f) + " (" + f.getName() + ")";
                }
                html = html.replaceAll("<h2>Summary</h2>", "<h2 id=\"report_" + suffix + "\">Overview: " + title + "</h2>");

                for (int i=0;i < 10;i++)
                {
                    html = html.replaceAll("#M" + i, "#M" + i + "_" + suffix);
                    html = html.replaceAll("id=\"M" + i, "id=\"M" + i + "_" + suffix);
                }

                //only load the new CSS file for the 1st file
                html = html.replaceAll("@@css@@", css);
                css = "";
                html = html.replaceAll("<link href=\"\" type=\"text/css\" rel=\"stylesheet\">\n", "");

                output += delim + html;
                delim = "<hr>";

                //also build a header:
                header += "<li><a href=\"#report_" + suffix + "\">" + title + "</a></li>";

                //remove footer except on final file
                if (counter < inputFiles.size() - 1)
                {
                    output = output.replaceAll("<div class=\"footer\">.*</div>", "");
                }

                counter++;
            }

            if (inputFiles.size() > 1)
            {
                header += "</ul><p /></div><hr>";
                String tag = "<div class=\"fastqc\">";
                output = output.replace(tag, tag + header);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        if (!_cacheResults)
        {
            for (File f : filesCreated)
            {
                _logger.debug("deleting fastcq file: " + f.getPath());
                f.delete();
            }
        }

        return output;
    }

    private String readCompressedHtmlReport(File htmlFile) throws IOException
    {
        StringWriter writer = new StringWriter();
        try (InputStream is = new GZIPInputStream(new FileInputStream(htmlFile)))
        {
            IOUtils.copy(is, writer, StringUtilsLabKey.DEFAULT_CHARSET);
        }

        return writer.toString();
    }

    private File lookupFile(String path) throws FileNotFoundException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        DirectoryResource resource = (DirectoryResource)module.getModuleResolver().lookup(Path.parse(path));
        assert resource != null : "Unable to find resource with path: " + path;

        File file = null;
        for (Resource r : resource.list())
        {
            if (r instanceof FileResource)
            {
                file = ((FileResource) r).getFile().getParentFile();
                break;
            }
        }

        if (file == null)
            throw new FileNotFoundException("Not found: " + path);

        if (!file.exists())
            throw new FileNotFoundException("Not found: " + file.getPath());

        return file;
    }

    private List<String> getBaseParams() throws FileNotFoundException
    {
        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());

        int threads = getThreads();
        params.add("-Xmx" + (250 * threads) + "m");

        if (threads > 1)
        {
            params.add("-Dfastqc.threads=" + threads);
        }

        File libDir = new File(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getExplodedPath(), "lib");
        File apiLibDir = new File(ModuleLoader.getInstance().getModule("api").getExplodedPath(), "lib");
        File fastqcDir = new File(libDir.getParentFile(), "external/fastqc");
        File bzJar = new File(libDir, "bzip2-0.9.1.jar");
        if (!bzJar.exists())
            throw new RuntimeException("Not found: " + bzJar.getPath());

        File samJar = new File(libDir, "sam-1.96.jar");
        if (!samJar.exists())
            throw new RuntimeException("Not found: " + samJar.getPath());

        File commonsMath = new File(apiLibDir, "commons-math3-3.6.1.jar");
        if (!commonsMath.exists())
        {
            throw new RuntimeException("Not found: " + commonsMath.getPath());
        }

        List<String> classPath = new ArrayList<>();
        classPath.add(".");
        classPath.add(fastqcDir.getPath());
        classPath.add(samJar.getPath());
        classPath.add(bzJar.getPath());
        classPath.add(commonsMath.getPath());

        params.add("-classpath");
        params.add(StringUtils.join(classPath, File.pathSeparator));

        params.add("-Djava.awt.headless=true");

        return params;
    }

    private int getThreads()
    {
        return _threads;
    }

    public void setThreads(int threads)
    {
        _threads = threads;
    }

    private List<String> getParams(File f) throws FileNotFoundException
    {
        List<String> params = getBaseParams();

        params.add("-Dfastqc.output_dir=" + f.getParentFile().getAbsolutePath());
        //params.add("-Dfastqc.quiet=true");

        params.add("uk.ac.babraham.FastQC.FastQCApplication");

        return params;
    }
}

