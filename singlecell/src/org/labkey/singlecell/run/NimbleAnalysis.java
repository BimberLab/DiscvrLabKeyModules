package org.labkey.singlecell.run;

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
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;

public class NimbleAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public NimbleAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<NimbleAnalysis>
    {
        public Provider()
        {
            super("NimbleAnalysis", "Nimble", null, "This will run Nimble to generate a supplemental feature count matrix for the provided libraries", NimbleAlignmentStep.getToolParameters(), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js", "sequenceanalysis/field/GenomeField.js", "singlecell/panel/NimbleAlignPanel.js")), null);
        }

        @Override
        public NimbleAnalysis create(PipelineContext ctx)
        {
            return new NimbleAnalysis(this, ctx);
        }
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());

        List<Integer> genomeIds = helper.getGenomeIds();
        for (int id : genomeIds)
        {
            helper.prepareGenome(id);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        NimbleHelper helper = new NimbleHelper(getPipelineCtx(), getProvider(), getStepIdx());
        helper.doNimbleAlign(inputBam, output, rs, FileUtil.getBaseName(inputBam));

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
