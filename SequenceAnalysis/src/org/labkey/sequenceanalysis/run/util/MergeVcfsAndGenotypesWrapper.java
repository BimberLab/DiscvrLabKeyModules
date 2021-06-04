package org.labkey.sequenceanalysis.run.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 4/4/2017.
 */
public class MergeVcfsAndGenotypesWrapper extends AbstractDiscvrSeqWrapper
{
    public MergeVcfsAndGenotypesWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, List<File> inputVcfs, File outputVcf, List<String> options) throws PipelineJobException
    {
        execute(referenceFasta, inputVcfs, outputVcf, options, false);
    }

    public void execute(File referenceFasta, List<File> inputVcfs, File outputVcf, List<String> options, boolean inPriorityOrder) throws PipelineJobException
    {
        getLogger().info("Running DISCVR-seq MergeVcfsAndGenotypes");

        ensureDictionary(referenceFasta);
        try
        {
            for (File inputVcf : inputVcfs)
            {
                SequenceAnalysisService.get().ensureVcfIndex(inputVcf, getLogger());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("MergeVcfsAndGenotypes");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("--ignore-variants-starting-outside-interval");

        List<String> priorities = new ArrayList<>();
        int idx = 0;
        for (File f : inputVcfs)
        {
            idx++;

            String id = String.valueOf(idx);
            args.add("--variant" + (inPriorityOrder ? ":" + id : ""));
            priorities.add(id);
            args.add(f.getPath());
        }

        args.add("-O");
        args.add(outputVcf.getPath());

        if (inPriorityOrder)
        {
            args.add("-priority");
            args.add(StringUtils.join(priorities, ","));
        }

        if (options != null)
        {
            args.addAll(options);
        }

        execute(args);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputVcf.getPath());
        }
    }
}
