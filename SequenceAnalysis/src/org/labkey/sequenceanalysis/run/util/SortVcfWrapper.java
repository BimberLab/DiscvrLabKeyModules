package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: bimber
 * Date: 10/29/12
 * Time: 7:55 PM
 */
public class SortVcfWrapper extends PicardWrapper
{
    public SortVcfWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    /**
     * If the output file is null, the original will be deleted and replaced by the sorted vcf
     */
    public File sortVcf(File inputFile, @Nullable File outputFile, File sequenceDictionary) throws PipelineJobException
    {
        getLogger().info("Running SortVcf: " + inputFile.getPath());

        String gz = inputFile.getPath().toLowerCase().endsWith(".gz") ? ".gz" : "";
        File outputVcf = outputFile == null ? new File(getOutputDir(inputFile), FileUtil.getBaseName(inputFile) + ".sorted.vcf" + gz) : outputFile;

        List<String> params = getBaseArgs();
        params.add("I=" + inputFile.getPath());
        params.add("O=" + outputVcf.getPath());
        params.add("SEQUENCE_DICTIONARY=" + sequenceDictionary.getPath());

        execute(params);

        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputVcf.getPath());
        }

        if (outputFile == null)
        {
            try
            {
                getLogger().debug("replacing input file with sorted");
                inputFile.delete();
                FileUtils.moveFile(outputVcf, inputFile);

                //note: if there is a pre-existing index, we need to delete this since it is out of date
                File idx = new File(inputFile.getPath() + ".tbi");
                if (idx.exists())
                {
                    getLogger().debug("deleting VCF index: " + idx.getName());
                    idx.delete();
                }

                idx = new File(inputFile.getPath() + ".idx");
                if (idx.exists())
                {
                    getLogger().debug("deleting VCF index : " + idx.getName());
                    idx.delete();
                }

                //sortVcf should create indexes
                idx = new File(outputVcf.getPath() + ".tbi");
                if (idx.exists())
                {
                    getLogger().debug("moving created VCF index: " + idx.getName());
                    FileUtils.moveFile(idx, new File(inputFile.getPath() + ".tbi"));
                }

                idx = new File(outputVcf.getPath() + ".idx");
                if (idx.exists())
                {
                    getLogger().debug("moving created VCF index: " + idx.getName());
                    FileUtils.moveFile(idx, new File(inputFile.getPath() + ".idx"));
                }

                SequenceAnalysisService.get().ensureVcfIndex(inputFile, getLogger());

                return inputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(inputFile, getLogger());

                return outputFile;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    protected String getToolName()
    {
        return "SortVcf";
    }
}
