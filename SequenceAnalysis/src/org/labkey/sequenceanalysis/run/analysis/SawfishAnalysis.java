package org.labkey.sequenceanalysis.run.analysis;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsIndexer;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SawfishAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public SawfishAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<SawfishAnalysis>
    {
        public Provider()
        {
            super("sawfish", "Sawfish Analysis", null, "This will run sawfish SV dicvoery and calling on the selected BAMs", List.of(), null, null);
        }


        @Override
        public SawfishAnalysis create(PipelineContext ctx)
        {
            return new SawfishAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("discover");

        args.add("--bam");
        args.add(inputBam.getPath());

        // NOTE: sawfish stores the absolute path of the FASTA in the output JSON, so dont rely on working copies:
        args.add("--ref");
        args.add(referenceGenome.getSourceFastaFile().getPath());

        File svOutDir = new File(outputDir, "sawfish");
        args.add("--output-dir");
        args.add(svOutDir.getPath());

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            args.add("--threads");
            args.add(String.valueOf(maxThreads));
        }

        File bcf = new File(svOutDir, "candidate.sv.bcf");
        File bcfIdx = new File(bcf.getPath() + ".csi");
        if (bcfIdx.exists())
        {
            getPipelineCtx().getLogger().debug("BCF index already exists, reusing output");
        }
        else
        {
            new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(args);
        }

        if (!bcf.exists())
        {
            throw new PipelineJobException("Unable to find file: " + bcf.getPath());
        }

        output.addSequenceOutput(bcf, rs.getName() + ": sawfish", "Sawfish SV Discovery", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("SAWFISHPATH", "sawfish");
    }
}