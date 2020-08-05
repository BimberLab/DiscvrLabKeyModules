package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/2/2014.
 */
public class PARalyzerRunner extends AbstractCommandWrapper
{
    public PARalyzerRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File execute(File inputBam, File outDir, File twoBit, Map<String, String> toolParams) throws PipelineJobException
    {
        List<File> tmpFiles = new ArrayList<>();
        try
        {
            getLogger().info("Running PARalyzer using BAM: " + inputBam.getPath());
            setOutputDir(outDir);

            //ensure sort order
            File bam = inputBam;
            if (SequenceUtil.getBamSortOrder(inputBam) != SAMFileHeader.SortOrder.queryname)
            {
                getLogger().info("Queryname Sorting BAM: " + inputBam.getPath());
                SamSorter sortSamWrapper = new SamSorter(getLogger());

                bam = new File(outDir, FileUtil.getBaseName(inputBam) + ".querysorted.bam");
                tmpFiles.add(bam);
                sortSamWrapper.execute(inputBam, bam, SAMFileHeader.SortOrder.queryname);
            }

            //convert to SAM
            File sam = new File(outDir, FileUtil.getBaseName(inputBam) + ".sam");
            SamFormatConverterWrapper converterWrapper = new SamFormatConverterWrapper(getLogger());
            converterWrapper.execute(inputBam, sam, false);
            tmpFiles.add(sam);

            File ini = new File(outDir, FileUtil.getBaseName(inputBam) + ".paralyzer.ini");
            File clusterFile = new File(outDir, FileUtil.getBaseName(inputBam) + ".clusters.txt");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(ini)))
            {
                writer.write("SAM_FILE=" + sam.getPath() + "\n");
                writer.write("OUTPUT_CLUSTERS_FILE=" + clusterFile.getPath() + "\n");
                writer.write("GENOME_2BIT_FILE=" + twoBit.getPath() + "\n");

                if (!toolParams.containsKey("BANDWIDTH"))
                    writer.write("BANDWIDTH=3" + "\n");
                if (!toolParams.containsKey("CONVERSION"))
                    writer.write("CONVERSION=T>C" + "\n");
                if (!toolParams.containsKey("EXTEND_BY_READ") && !toolParams.containsKey("HAFFNER_APPROACH") && !toolParams.containsKey("ADDITIONAL_NUCLEOTIDES_BEYOND_SIGNAL"))
                    writer.write("EXTEND_BY_READ" + "\n");

                for (String key : toolParams.keySet())
                {
                    writer.write(key + "=" + (toolParams.get(key) == null ? "" : toolParams.get(key)) + "\n");
                }
            }

            List<String> params = new ArrayList<>();
            params.add(getExe().getPath());
            params.add("4g");
            params.add(ini.getPath());

            execute(params);

            if (!clusterFile.exists())
                throw new PipelineJobException("PARalyzer output not created, expected: " + clusterFile.getPath());

            return clusterFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("PARALYZERPATH", "PARalyzer");
    }
}
