package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/3/2014.
 */
public class BamStatsRunner
{
    private File _tmpDir = null;
    private List<File> _sequenceFiles;
    private Map<File, File> _outputDirs = new HashMap<>();
    private String _commandOutput = null;
    private static final Logger _log = Logger.getLogger(BamStatsRunner.class);

    public BamStatsRunner()
    {
    }

    public void execute(List<File> sequenceFiles) throws FileNotFoundException
    {
        _sequenceFiles = sequenceFiles;

        //remove duplicates
        List<File> tmpList = new ArrayList<>();
        for (File f : _sequenceFiles)
        {
            if (!tmpList.contains(f))
                tmpList.add(f);
        }
        _sequenceFiles = tmpList;

        //verify names are unique
        for (File f : _sequenceFiles)
        {
            List<String> params = getParams();

            params.add("-i");
            params.add(f.getAbsolutePath());

            params.add("-o");
            File outputDir = new File(getTmpDir(), new GUID().toString());
            _outputDirs.put(f, outputDir);
            params.add("\"" + outputDir.getPath() + "\"");
            _log.info(StringUtils.join(params, " "));

            ProcessBuilder pb = new ProcessBuilder(params);
            pb.redirectErrorStream(true);

            try
            {
                pb.directory(outputDir.getParentFile());
                Process p = pb.start();
                StringBuffer output = new StringBuffer();

                try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
                {
                    String line;
                    while ((line = procReader.readLine()) != null)
                    {
                        output.append(line);
                        output.append(System.getProperty("line.separator"));
                        _log.info(line);
                    }

                    int returnCode = p.waitFor();
                    _commandOutput = output.toString();

                    if (returnCode != 0)
                    {
                        throw new IOException("BamStats failed with error code " + returnCode + " - " + output.toString());
                    }
                }
                catch (Exception eio)
                {
                    throw new RuntimeException("Failed writing output for process: "  + output.toString(), eio);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private List<String> getParams() throws FileNotFoundException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-Xmx6g");

        File baseDir = lookupFile("external/bamstats");

        List<String> classPath = new ArrayList<>();

        classPath.add(".");
        classPath.add(new File(baseDir, "lib/jopt-simple-3.2.jar").getPath());
        classPath.add(new File(baseDir, "lib/poi-3.7-20101029.jar").getPath());
        classPath.add(new File(baseDir, "lib/sam-1.50.jar").getPath());
        classPath.add(new File(baseDir, "BAMStats-1.25.jar").getPath());

        params.add("-classpath");
        params.add(StringUtils.join(classPath, File.pathSeparator));

        params.add("-jar");
        params.add(new File(baseDir, "BAMStats-1.25.jar").getPath());

        params.add("-v");
        params.add("html");
        params.add("-d");
        params.add("-l");
        params.add("-m");
        params.add("-q");

        return params;
    }

    private File getTmpDir()
    {
        try
        {
            if (_tmpDir == null)
                _tmpDir = FileUtil.getAbsoluteCaseSensitiveFile(FileUtil.createTempDirectory("bamstats_"));

            if (!_tmpDir.exists())
                _tmpDir.mkdirs();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return _tmpDir;
    }

    private File lookupFile(String path) throws FileNotFoundException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse(path));
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

    public String processOutput(Container c)
    {
        String output = "";
        String header = "<div><h2>File Summary:</h2><ul>";

        try
        {
            String delim = "";
            int counter = 0;
            for (File f : _sequenceFiles)
            {
                File origFile = f;
                File htmlFile = _outputDirs.get(f);
                String guid = htmlFile.getName();
                File resourcesDir = new File(htmlFile.getParentFile(), guid + ".data");

                if (!htmlFile.exists())
                {
                    output += "<p>Unable to find output for file: " + origFile.getName() + ".  This probably indicates a problem running BAMStats.</p>";
                    if (_commandOutput != null)
                    {
                         _log.error("Something went wrong running BAMStats.  Here is the program's output, which might help debug the problem");
                        _log.error(_commandOutput);
                    }
                    continue;
                }

                String html = readFileToString(htmlFile, c, "");
                if (html.isEmpty())
                {
                    html += "Something went wrong running BAMStats. Please contact your administrator for more detail";

                    if (_commandOutput != null)
                    {
                        _log.error("Something went wrong running BAMStats.  Here is the program's output, which might help debug the problem");
                        _log.error(_commandOutput);
                    }
                }

                output += delim + html;
                delim = "<hr>";

                //also build a header:
                //header += "<li><a href=\"#report_" + suffix + "\">" + origFile.getName() + "</a></li>";

                //remove footer except on final file
                if (counter < _sequenceFiles.size() - 1)
                {
                    output = output.replaceAll("<div class=\"footer\">.*</div>", "");
                }

                File[] children = resourcesDir.listFiles(new FilenameFilter()
                {
                    @Override
                    public boolean accept(File dir, String name) {
                        return "html".equals(FileUtil.getExtension(name));
                    }
                });

                String refHtml = "<hr>";
                for (File child : children)
                {
                    List<String> tokens = new ArrayList(Arrays.asList(StringUtils.split(child.getName(), "_")));
                    tokens.remove(tokens.size() - 1);
                    String refName = StringUtils.join(tokens, "_");

                    String h = readFileToString(child, c, "/" + guid + ".data");
                    h = h.replace("<h1>", "<h2>");
                    h = h.replace("</h1>", "</h2>");
                    h = "<span id=\"" + refName + "\"></span>" + h;

                    refHtml += h;
                    child.delete();
                }

                output += refHtml;

                counter++;
            }

            if (_sequenceFiles.size() > 1)
            {
                header += "</ul><p /></div><hr>";
                String tag = "<div class=\"fastqc\">";
                output = output.replace(tag, tag + header);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return output;
    }

    private String readFileToString(File htmlFile, Container c, String folderPrefix) throws IOException
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
            if (is != null)
                is.close();
        }

        String html = writer.toString();
        //replace links
        DetailsURL d = DetailsURL.fromString("/SequenceAnalysis/downloadTempImage.view?");
        ActionURL a = d.copy(c).getActionURL();
        a.addParameter("directory", FileUtil.getBaseName(_tmpDir) + folderPrefix);
        String url = a.toString();
        url += "&fileName=";
        html = html.replaceAll("href=\"", "href=\"" + url);
        html = html.replaceAll("src=\"", "src=\"" + url);

        //remove tags
        html = html.replaceAll("<h1>1. Summary</h1>", "");
        html = html.replaceAll("<body>", "");
        html = html.replaceAll("<head><title>Summary</title></head>", "");
        html = html.replaceAll("<html>", "");
        html = html.replaceAll("providing descriptive statistics for each chromosome.</p>", "providing a summary for the BAM as a whole, along with breakdowns per reference sequence.</p>");

        return html;
    }
}
