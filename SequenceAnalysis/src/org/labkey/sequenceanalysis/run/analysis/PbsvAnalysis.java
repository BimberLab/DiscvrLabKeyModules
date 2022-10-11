package org.labkey.sequenceanalysis.run.analysis;

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
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.TabixRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PbsvAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public PbsvAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<PbsvAnalysis>
    {
        public Provider()
        {
            super("pbsv", "PBSV Analysis", null, "This will run pbsv on the selected BAMs.", Arrays.asList(

            ), null, null);
        }


        @Override
        public PbsvAnalysis create(PipelineContext ctx)
        {
            return new PbsvAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("discover");
        args.add(inputBam.getPath());
        File svOut = new File(outputDir, FileUtil.getBaseName(inputBam) + ".svsig.gz");
        args.add(svOut.getPath());

        new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(args);

        if (!svOut.exists())
        {
            throw new PipelineJobException("Unable to find file: " + svOut.getPath());
        }

        output.addSequenceOutput(svOut, rs.getName() + ": pbsv", "PBSV Output", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        // Ensure we create index:
        TabixRunner tabix = new TabixRunner(getPipelineCtx().getLogger());
        List<String> args2 = Arrays.asList(tabix.getExe().getPath(), "-f", "-s", "3", "-b", "4", "-e", "4", "-c", "#", svOut.getPath());
        tabix.execute(args2);

        File idx = new File(svOut.getPath() + ".tbi");
        if (!idx.exists())
        {
            throw new PipelineJobException("Missing index: " + idx.getPath());
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("PBSVPATH", "pbsv");
    }
}