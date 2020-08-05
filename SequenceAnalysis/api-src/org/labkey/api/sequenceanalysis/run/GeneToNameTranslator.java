package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

public class GeneToNameTranslator
{
    private int _noGeneId = 0;
    private File _geneFile;

    private Map<String, Map<String, String>> geneMap = new HashMap<>(5000);

    public GeneToNameTranslator(File geneFile, Logger log) throws PipelineJobException
    {
        _geneFile = geneFile;

        process(log);
    }
    
    private void process(Logger log) throws PipelineJobException
    {
        boolean isGTF = "gtf".equalsIgnoreCase(FileUtil.getExtension(_geneFile));

        try (BufferedReader gtfReader = Readers.getReader(_geneFile))
        {
            Pattern geneIdPattern = isGTF ? Pattern.compile("((gene_id) \")([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE) : Pattern.compile("((gene_id|geneID)=)([^;]+)(.*)", Pattern.CASE_INSENSITIVE);

            Map<String, Pattern> patternMap = new HashMap<>();
            if (isGTF)
            {
                patternMap.put("transcript_id", Pattern.compile("((transcript_id \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_name", Pattern.compile("((gene_name \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_description", Pattern.compile("((gene_description \"))([^\"]+)\"(.*)", Pattern.CASE_INSENSITIVE));
            }
            else
            {
                //GFF
                patternMap.put("transcript_id", Pattern.compile("((transcript_id=))([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_name", Pattern.compile("((gene_name|gene)[=:])([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
                patternMap.put("gene_description", Pattern.compile("((product|description)=)([^;]+)(.*)", Pattern.CASE_INSENSITIVE));
            }

            String line;
            while ((line = gtfReader.readLine()) != null)
            {
                if (line.startsWith("#"))
                {
                    continue;
                }

                Matcher m1 = geneIdPattern.matcher(line);
                if (m1.find())
                {
                    Map<String, String> map = new HashMap<>();
                    for (String field : patternMap.keySet())
                    {
                        Pattern pattern = patternMap.get(field);
                        Matcher m2 = pattern.matcher(line);
                        if (m2.find())
                        {
                            String val = m2.group(3);
                            map.put(field, val);
                        }
                    }

                    String geneId = m1.group(3);
                    geneMap.put(geneId, map);
                }
                else
                {
                    if (_noGeneId < 10)
                    {
                        log.warn("skipping GTF/GFF line because it lacks a gene Id: ");
                        log.warn(line);

                        if (_noGeneId == 9)
                        {
                            log.warn("future warnings will be skipped");
                        }
                    }

                    _noGeneId++;
                }
            }

            if (_noGeneId > 0)
            {
                log.warn("total lines lacking a gene id: " + _noGeneId);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public Map<String, Map<String, String>> getGeneMap()
    {
        return geneMap;
    }
}
