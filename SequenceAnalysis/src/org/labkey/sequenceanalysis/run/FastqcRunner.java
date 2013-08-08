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
package org.labkey.sequenceanalysis.run;

import org.apache.commons.io.IOUtils;
import org.labkey.api.data.Container;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/23/12
 * Time: 2:02 PM
 */

// Handles the basics of testing the Fastqc configuration, creating the fastqc command line, and invoking fastqc. This code
// is patterned after DotRunner.
public class FastqcRunner
{
    private File _tmpDir = null;
    private List<File> _sequenceFiles;
    private Map<File, File> _unzippedFiles = new HashMap<>();

    public FastqcRunner()
    {
    }

    public List<File> getSequenceFiles()
    {
        return _sequenceFiles;
    }

    public void execute(List<File> sequenceFiles) throws FileNotFoundException
    {
        _sequenceFiles = sequenceFiles;

        //remove duplicates
        List<File> tmpList = new ArrayList<>();
        for (File f: _sequenceFiles)
        {
            if (!tmpList.contains(f))
                tmpList.add(f);
        }
        _sequenceFiles = tmpList;

        List<String> params = getParams();

        //verify names are unique
        Set<String> basenames = new HashSet<>();
        for (File f : _sequenceFiles)
        {
            String bn = getExpectedBasename(f);
            if(basenames.contains(bn))
            {
                File copy = new File(getTmpDir(), (new GUID()).toString() + "_" + f.getName());
                try
                {
                    FileUtil.copyFile(f, copy);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
                _unzippedFiles.put(f, copy);
                f = copy;
            }

            params.add(f.getAbsolutePath());
            basenames.add(bn);
        }

        ProcessBuilder pb = new ProcessBuilder(params);
        pb.directory(getTmpDir());
        pb.redirectErrorStream(true);

        try
        {
            Process p = pb.start();
            BufferedReader procReader = null;
            StringBuffer output = new StringBuffer();

            try
            {
                procReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));
                }

                int returnCode = p.waitFor();

                if (returnCode != 0)
                {
                    throw new IOException("FastQC failed with error code " + returnCode + " - " + output.toString());
                }
            }
            catch (IOException eio)
            {
                throw new RuntimeException("Failed writing output for process in '" + pb.directory().getPath() + "'.", eio);
            }
            finally
            {
                if (procReader != null)
                    try {procReader.close();} catch(IOException ignored) {}
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            //cleanup unwanted outputs and unzipped files
            for (File f : _sequenceFiles)
            {
                if(_unzippedFiles.containsKey(f))
                {
                    f = _unzippedFiles.get(f);
                    if(!f.delete())
                    {
                        f.deleteOnExit();
                    }
                }

                File zip = new File(_tmpDir, getExpectedBasename(f) + "_fastqc.zip");
                if(!zip.delete())
                {
                    zip.deleteOnExit();
                }
            }
        }
    }

    private String getExpectedBasename(File f)
    {
        String basename = FileUtil.getBaseName(f);
        FileType gz = new FileType(".gz");
        if(gz.isType(f))
        {
            basename = FileUtil.getBaseName(basename);
        }
        return basename;
    }

    public String processOutput(Container c)
    {
        String output = "";
        String header = "<div class=\"fastqc_overview\"><h2>File Summary:</h2><ul>";

        DetailsURL d = DetailsURL.fromString("/SequenceAnalysis/downloadFastqImage.view?");

        try
        {
            String delim = "";
            String css = AppProps.getInstance().getContextPath() + "/SequenceAnalysis/fastqc.css";
            int counter = 0;
            for (File f : _sequenceFiles)
            {
                File origFile = f;
                f = _unzippedFiles.containsKey(f) ? _unzippedFiles.get(f) : f;
                String basename = getExpectedBasename(f);
                File outputDir = new File(getTmpDir().getAbsolutePath(), basename + "_fastqc");
                File htmlFile = new File(outputDir, "fastqc_report.html");

                if (!htmlFile.exists())
                {
                    output += "<p>Unable to find output for file: " + origFile.getName() + "</p>";
                    continue;
                }

                String html = readHtmlReport(htmlFile);
                html = html.replaceAll("Icons/", AppProps.getInstance().getContextPath() + "/SequenceAnalysis/icons/");

                ActionURL a = d.copy(c).getActionURL();
                a.addParameter("directory", FileUtil.getBaseName(_tmpDir));
                a.addParameter("fileName", basename + "_fastqc");

                String url = a.toString();
                url += "&image=";

                html = html.replaceAll("Images/", url);

                //add an outer DIV so we can apply styles only to the report
                html = html.replaceAll("<body>", "<div class=\"fastqc\">");
                html = html.replaceAll("</body>", "</div>");


                //update IDs so links will point to correct file after concatenated:
                String suffix = f.getName().replaceAll("\\.", "_");
                html = html.replaceAll("<h2>Summary</h2>", "<h2 id=\"report_" + suffix + "\">Overview: " + origFile.getName() + "</h2>");

                for (int i=0;i < 10;i++)
                {
                    html = html.replaceAll("#M" + i, "#M" + i + "_" + suffix);
                    html = html.replaceAll("id=\"M" + i, "id=\"M" + i + "_" + suffix);
                }

                //only load the new CSS file for the 1st file
                html = html.replaceAll("@@css@@", css);
                css = "";
                html = html.replaceAll("<link href=\"\" type=\"text/css\" rel=\"stylesheet\">\n", "");

                File iconDir = new File(outputDir, "Icons");
                for (File icon : iconDir.listFiles())
                {
                    icon.delete();
                }
                iconDir.delete();
                htmlFile.delete();

                //NOTE: images will be deleted when they are loaded using DownloadFastqImageAction
                //File imageDir = new File(outputDir, "Images");
                //imageDir.deleteOnExit();

                (new File(outputDir, "fastqc_data.txt")).delete();
                (new File(outputDir, "summary.txt")).delete();

                output += delim + html;
                delim = "<hr>";

                //also build a header:
                header += "<li><a href=\"#report_" + suffix + "\">" + origFile.getName() + "</a></li>";

                //remove footer except on final file
                if (counter < _sequenceFiles.size() - 1)
                {
                    output = output.replaceAll("<div class=\"footer\">.*</div>", "");
                }

                counter++;
            }

            if(_sequenceFiles.size() > 1)
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

        return output;
    }

    private String readHtmlReport(File htmlFile) throws IOException
    {
        StringWriter writer = new StringWriter();
        FileInputStream is = null;
        try
        {
            is = new FileInputStream(htmlFile);
            IOUtils.copy(is, writer, "UTF-8");
        }
        finally
        {
            if(is != null)
                is.close();
        }

        return writer.toString();
    }

    private List<String> getParams() throws FileNotFoundException
    {
        return getParams(false);
    }

    private File lookupFile(String path) throws FileNotFoundException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse(path));
        File file = null;
        for (Resource r : resource.list())
        {
            if(r instanceof FileResource)
            {
                file = ((FileResource) r).getFile().getParentFile();
                break;
            }
        }

        if(file == null)
            throw new FileNotFoundException("Not found: " + path);

        if(!file.exists())
            throw new FileNotFoundException("Not found: " + file.getPath());

        return file;
    }

    private List<String> getParams(boolean showVersion) throws FileNotFoundException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-Xmx250m");

        File fastqcDir = lookupFile("external/fastqc");

        List<String> classPath = new ArrayList<>();
        File samJar = SequenceAnalysisManager.getSamJar();

        File bzJar = lookupFile("lib");
        bzJar = new File(bzJar, "jbzip2-0.9.jar");
        if(!bzJar.exists())
            throw new RuntimeException("Not found: " + bzJar.getPath());

        classPath.add(".");
        classPath.add(fastqcDir.getPath());
        classPath.add(samJar.getPath());
        classPath.add(bzJar.getPath());

        params.add("-classpath");
        params.add(StringUtils.join(classPath, File.pathSeparator));

        if(showVersion)
        {
            params.add("-Dfastqc.show_version=true");
        }
        else
        {
            params.add("-Dfastqc.output_dir=" + getTmpDir().getAbsolutePath());
            params.add("-Dfastqc.quiet=true");
        }

        params.add("uk.ac.bbsrc.babraham.FastQC.FastQCApplication");

        return params;
    }

    private File getTmpDir()
    {
        try
        {
            if(_tmpDir == null)
                _tmpDir = FileUtil.getAbsoluteCaseSensitiveFile(FileUtil.createTempDirectory("fastqc_"));

            if (!_tmpDir.exists())
                _tmpDir.createNewFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return _tmpDir;
    }

    public static String getConfigurationErrorHtml(Exception e)
    {
        if (e.getMessage() != null)
        {
            return getConfigurationErrorHtml(e.getMessage());
        }
        else
        {
            return getConfigurationErrorHtml(e.toString());
        }
    }

    public static String getConfigurationErrorHtml(String message)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Unable to display FASTQC output: cannot run ");
        //sb.append(exePath);
        sb.append(" due to an error.\n<BR><pre>");
        sb.append(PageFlowUtil.filter(message));
        sb.append("</pre>");
        sb.append("This likely means either FASTQC is not installed or the executable is not in the PATH.  FastQC can be obtained <a href=\"http://www.bioinformatics.babraham.ac.uk/projects/fastqc/\">here</a>");
        sb.append(".<br>");

        return sb.toString();
    }
}

