package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by bimber on 8/3/2014.
 */
public class QualiMapRunner
{
    private static final Logger _log = Logger.getLogger(QualiMapRunner.class);
    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.sequenceanalysis.settings";
    public final static String QUALIMAP_DIR = "qualiMapDir";

    public QualiMapRunner()
    {
    }

    public static File getQualiMapDir()
    {
        Map<String, String> props = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN);
        if (props.containsKey(QUALIMAP_DIR))
        {
            return new File(props.get(QUALIMAP_DIR));
        }

        return null;
    }

    public static void setQualiMapDir(String dir)
    {
        PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(CONFIG_PROPERTY_DOMAIN, true);

        String binDir = StringUtils.trimToNull(dir);
        configMap.put(QUALIMAP_DIR, binDir);

        PropertyManager.saveProperties(configMap);
    }

    public static void isQualiMapDirValid() throws ConfigurationException
    {
        File qualimapDir = getQualiMapDir();
        if (qualimapDir == null)
        {
            throw new ConfigurationException("QualiMap directory not set");
        }

        if (!qualimapDir.exists())
        {
            throw new ConfigurationException("QualiMap directory does not exist: " + qualimapDir.getPath());
        }

        File qualimapJar = new File(qualimapDir, "qualimap.jar");
        if (!qualimapJar.exists())
        {
            throw new ConfigurationException("qualimap.jar was not found in the directory: " + qualimapDir.getPath());
        }
    }

    public String execute(List<File> sequenceFiles) throws IOException, ConfigurationException
    {
        isQualiMapDirValid();

        //remove duplicates
        List<File> tmpList = new ArrayList<>();
        for (File f : sequenceFiles)
        {
            if (!tmpList.contains(f))
            {
                tmpList.add(f);
            }
        }
        sequenceFiles = tmpList;

        Map<File, File> outputMap = new HashMap<>();
        for (File bam : sequenceFiles)
        {
            boolean shouldRun = true;

            File outputDir = getExpectedOutputFile(bam);
            if (outputDir.exists())
            {
                File htmlFile = new File(outputDir, "qualimapReport.html");
                if (htmlFile.exists())
                {
                    _log.info("cached qualimap found for: " + bam.getPath());
                    shouldRun = false;
                    outputMap.put(bam, htmlFile);
                }
                else
                {
                    //indicates some sort of problem with the cached files
                    FileUtils.deleteDirectory(outputDir);
                }
            }

            if (shouldRun)
            {
                List<String> params = getParams(bam);

                _log.info(StringUtils.join(params, " "));

                ProcessBuilder pb = new ProcessBuilder(params);
                pb.redirectErrorStream(true);

                try
                {
                    pb.directory(bam.getParentFile());
                    Process p = pb.start();
                    StringBuilder output = new StringBuilder();

                    try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
                    {
                        String line;
                        while ((line = procReader.readLine()) != null)
                        {
                            output.append(line).append(System.getProperty("line.separator"));
                            _log.info(line);
                        }

                        int returnCode = p.waitFor();
                        if (returnCode != 0)
                        {
                            throw new IOException("QualiMap failed with error code " + returnCode + " - " + output.toString());
                        }
                    }
                    catch (Exception eio)
                    {
                        throw new RuntimeException("Failed writing output for process: " + output.toString(), eio);
                    }
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                File htmlFile = new File(outputDir, "qualimapReport.html");
                if (htmlFile.exists())
                {
                    prepareHtmlFile(bam, htmlFile);
                    outputMap.put(bam, htmlFile);
                }
                else
                {
                    outputMap.put(bam, null);  //will error downstream
                }
            }
        }

        return processOutput(outputMap);
    }

    private List<String> getParams(File inputFile) throws FileNotFoundException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-Xms32m");
        params.add("-Xmx1g");

        File qualiMapDir = getQualiMapDir();

        List<String> classPath = new ArrayList<>();

        classPath.add(new File(qualiMapDir, "lib/*").getPath());
        classPath.add(new File(qualiMapDir, "qualimap.jar").getPath());

        params.add("-classpath");
        params.add(StringUtils.join(classPath, File.pathSeparator));

        params.add("org.bioinfo.ngs.qc.qualimap.main.NgsSmartMain");

        params.add("bamqc");

        params.add("-bam");
        params.add(inputFile.getPath());

        params.add("-outdir");
        params.add(getExpectedOutputFile(inputFile).getPath());

        return params;
    }

    private File getExpectedOutputFile(File bamFile)
    {
        return new File(bamFile.getParentFile(), FileUtil.getBaseName(bamFile) + "_qualimapReport");
    }

    private String processOutput(Map<File, File> outputFiles)
    {
        String output = "";
        String header = "<h3>Files:</h3>";

        try
        {
            String delim = "";
            int idx = 0;
            for (File bam : outputFiles.keySet())
            {
                output += "<div id=\"report_" + idx + "\"></div>";

                File htmlFile = outputFiles.get(bam);
                if (htmlFile == null || !htmlFile.exists())
                {
                    output += "<p>Unable to find output for file: " + bam.getName() + ".  This probably indicates a problem running QualiMap.</p>";
                    continue;
                }

                String html = readFileToString(htmlFile);
                if (html.isEmpty())
                {
                    html += "Something went wrong running QualiMap. Please contact your administrator for more detail";
                }

                output += delim + html;
                //delim = "<hr>";

                //also build a header:
                header += "<h3><a href=\"#report_" + idx + "\">- " + bam.getName() + "</a></h3>";
                idx++;
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        if (outputFiles.size() > 1)
        {
            header += "<br><br>";
            output = header + output;
        }

        return output;
    }

    private String readFileToString(File htmlFile) throws IOException
    {
        StringWriter writer = new StringWriter();
        try (FileInputStream is = new FileInputStream(htmlFile))
        {
            IOUtils.copy(is, writer, "UTF-8");
        }

        return writer.toString();
    }

    private void prepareHtmlFile(File bam, File htmlFile) throws IOException
    {
        String html = readFileToString(htmlFile);

        //update CSS links
        html = html.replaceAll("\"css/", "\"" + AppProps.getInstance().getContextPath() + "/sequenceanalysis/qualiMap/");

        //remove tags
        html = html.replaceAll("<!DOCTYPE HTML>", "");
        html = html.replaceAll("</div\n", "</div>");  //this seems to be an error in their HTML?
        html = html.replaceAll("<html>", "");
        html = html.replaceAll("</html>", "");
        html = html.replaceAll("<body>", "");
        html = html.replaceAll("</body>", "");
        html = html.replaceAll("<title>(.*)</title>", "");
        html = Pattern.compile("<div class=\"footer-wrapper\">(.*)<!-- footer -->(.*)</div>", Pattern.DOTALL).matcher(html).replaceAll("");

        String fileGuid = new GUID().toString();

        //delete CSS
        FileUtils.deleteDirectory(new File(htmlFile.getParentFile(), "css"));
        FileUtils.deleteDirectory(new File(htmlFile.getParentFile(), "raw_data_qualimapReport"));

        //update images:
        File imgDir = new File(htmlFile.getParentFile(), "images_qualimapReport");
        if (imgDir.exists())
        {
            for (File child : imgDir.listFiles())
            {
                String encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(child));
                html = html.replaceAll("src=\"images_qualimapReport\\\\" + child.getName() + "\">", "src=\"data:image/png;base64," + encoded + "\">");
                html = html.replaceAll("href=\"#" + child.getName() + "\"", "href=\"#" + fileGuid + "_" + child.getName() + "\"");
                html = html.replaceAll("name=\"" + child.getName() + "\"", "name=\"" + fileGuid + "_" + child.getName() + "\"");

                child.delete();
            }
            FileUtils.deleteDirectory(imgDir);
        }

        //also update other links
        html = html.replaceAll("href=\"#input\"", "href=\"#" + fileGuid + "_input\"");
        html = html.replaceAll("name=\"input\"", "name=\"" + fileGuid + "_input\"");
        html = html.replaceAll("href=\"#summary\"", "href=\"#" + fileGuid + "_summary\"");
        html = html.replaceAll("name=\"summary\"", "name=\"" + fileGuid + "_summary\"");

        //update header
        html = html.replaceAll("Qualimap Report: BAM QC", "Qualimap Report: " + bam.getName());

        htmlFile.delete();
        htmlFile.createNewFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFile)))
        {
            writer.write(html);
        }
    }
}
