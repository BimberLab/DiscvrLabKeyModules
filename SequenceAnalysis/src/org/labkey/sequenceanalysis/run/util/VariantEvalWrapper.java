package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 8/10/2014.
 */
public class VariantEvalWrapper extends AbstractGatk4Wrapper
{
    public VariantEvalWrapper(Logger log)
    {
        super(log);
    }

    public void executeEval(File referenceFasta, File inputVcf, File outputFile, String setName, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        getLogger().info("Running GATK 4 VariantEval");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("VariantEval");
        args.add("-R");
        args.add(referenceFasta.getPath());

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                args.add("-L");
                args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        args.add("--eval:" + setName);
        args.add(inputVcf.getPath());

        args.add("-O");
        args.add(outputFile.getPath());

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        replaceWhitespace(outputFile);
    }

    private void replaceWhitespace(File outputFile) throws PipelineJobException
    {
        getLogger().info("replacing whitespace with tabs");
        new SimpleScriptWrapper(getLogger()).execute(Arrays.asList("sed", "-i", "s/ [ ]\\+/\\t/g", outputFile.getPath()));
        new SimpleScriptWrapper(getLogger()).execute(Arrays.asList("sed", "-i", "s/^[ ]\\+//g", outputFile.getPath()));
    }

    public void executeEvalBySample(File referenceFasta, File inputVcf, File outputFile, String setName, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        getLogger().info("Running GATK VariantEval");

        ensureDictionary(referenceFasta);

        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("VariantEval");
        args.add("-R");
        args.add(referenceFasta.getPath());

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                args.add("-L");
                args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        args.add("-ST");
        args.add("Sample");
        args.add("-noEV");

        args.add("-EV");
        args.add("CompOverlap");
        args.add("-EV");
        args.add("CountVariants");
        args.add("-EV");
        args.add("IndelLengthHistogram");
        args.add("-EV");
        args.add("IndelSummary");
        args.add("-EV");
        args.add("MultiallelicSummary");
        args.add("-EV");
        args.add("ThetaVariantEvaluator");
        args.add("-EV");
        args.add("TiTvVariantEvaluator");
        args.add("-EV");
        args.add("ValidationReport");

        args.add("--eval:" + setName);
        args.add(inputVcf.getPath());

        args.add("-O");
        args.add(outputFile.getPath());

        execute(args);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected output not found: " + outputFile.getPath());
        }

        replaceWhitespace(outputFile);
    }
}
