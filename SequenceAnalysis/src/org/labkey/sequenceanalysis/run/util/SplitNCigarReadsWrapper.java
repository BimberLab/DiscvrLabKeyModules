package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileHeader;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/8/2014.
 */
public class SplitNCigarReadsWrapper extends AbstractGatkWrapper
{
    public SplitNCigarReadsWrapper(Logger log)
    {
        super(log);
    }

    public void execute(File referenceFasta, File inputBam, File outputBam, boolean doReassignMappingQual) throws PipelineJobException
    {
        getLogger().info("Running GATK SplitNCigarReads");

        ensureDictionary(referenceFasta);
        SequencePipelineService.get().ensureBamIndex(inputBam, getLogger(), false);

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
        args.add("-jar");
        args.add(getJAR().getPath());
        args.add("-T");
        args.add("SplitNCigarReads");
        args.add("-R");
        args.add(referenceFasta.getPath());
        args.add("-I");
        args.add(inputBam.getPath());
        args.add("-o");
        args.add(outputBam.getPath());
        args.add("-U");
        args.add("ALLOW_N_CIGAR_READS");

        if (doReassignMappingQual)
        {
            //NOTE: STAR assigns a mapping quality of 255 to good alignments, which is intepreted as unknown by GATK, so we reassign here
            args.add("-rf");
            args.add("ReassignOneMappingQuality");
            args.add("-RMQF");
            args.add("255");
            args.add("-RMQT");
            args.add("60");
            //args.add("-DMQ");
            //args.add("60");
        }

        execute(args);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputBam.getPath());
        }

        try
        {
            SAMFileHeader.SortOrder order = SequenceUtil.getBamSortOrder(outputBam);
            if (SAMFileHeader.SortOrder.coordinate != order)
            {
                getLogger().info("coordinate sorting BAM, order was: " + (order == null ? "not provided" : order.name()));
                new SamSorter(getLogger()).execute(outputBam, null, SAMFileHeader.SortOrder.coordinate);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
