package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMFileHeader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpecHlaAnalysis extends AbstractCommandPipelineStep<SimpleScriptWrapper> implements AnalysisStep
{
    public SpecHlaAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new SimpleScriptWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<SpecHlaAnalysis>
    {
        public Provider()
        {
            super("SpecHlaStep", "SpecHLA", null, "This will run SpecHLA for HLA genotyping from WGS/WXS data. This should use a BAM aligned to a custom HLA DB, rather than aligned to the full genome", Arrays.asList(

            ), null, "https://github.com/deepomicslab/SpecHLA/");
        }

        @Override
        public SpecHlaAnalysis create(PipelineContext ctx)
        {
            return new SpecHlaAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File subsetBam = FileUtil.appendName(outputDir, FileUtil.getBaseName(inputBam) + ".subset.bam");
        SamtoolsRunner sr = new SamtoolsRunner(getWrapper().getLogger());
        sr.execute(Arrays.asList(
                sr.getSamtoolsPath().getPath(),
                "view",
                "-h",
                "-F", "12", //This selects pairs where either mate is mapped
                "-o", subsetBam.getPath(),
                inputBam.getPath()
        ));
        output.addIntermediateFile(subsetBam);

        File queryNameSortBam = new SamSorter(getPipelineCtx().getLogger()).execute(subsetBam, FileUtil.appendName(outputDir, FileUtil.getBaseName(inputBam) + ".querySort.bam"), SAMFileHeader.SortOrder.queryname);
        output.addIntermediateFile(queryNameSortBam);

        File fq1 = FileUtil.appendName(outputDir, FileUtil.getBaseName(inputBam) + ".R1.fastq.gz");
        File fq2 = FileUtil.appendName(outputDir, FileUtil.getBaseName(inputBam) + ".R2.fastq.gz");
        sr.execute(Arrays.asList(
                sr.getSamtoolsPath().getPath(),
                "fastq",
                "-1",
                fq1.getPath(),
                "-2",
                fq2.getPath(),
                queryNameSortBam.getPath()
        ));
        output.addIntermediateFile(fq1);
        output.addIntermediateFile(fq2);

        File specHlaExe = AbstractCommandWrapper.resolveFileInPath("spechla", null, true);

        List<String> toRun = new ArrayList<>(Arrays.asList(
            specHlaExe.getPath(),
            "-n",
            "specHLA",
            "-u",
            "1", // 1 = exon. 0 = full-length
            "-1",
            fq1.getPath(),
            "-2",
            fq2.getPath(),
            "-o",
            outputDir.getPath()
        ));

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getWrapper().getLogger());
        if (maxThreads != null)
        {
            toRun.add("-j");
            toRun.add(maxThreads.toString());
        }

        getWrapper().execute(toRun);

        File outFile = FileUtil.appendPath(outputDir, Path.parse("specHLA/hla.result.txt"));
        if (!outFile.exists())
        {
            throw new PipelineJobException("SpecHLA result file does not exist: " + outFile.getPath());
        }

        output.addSequenceOutput(outFile, FileUtil.getBaseName(inputBam) + ": HLA Typing", "specHLA Genotyping", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
