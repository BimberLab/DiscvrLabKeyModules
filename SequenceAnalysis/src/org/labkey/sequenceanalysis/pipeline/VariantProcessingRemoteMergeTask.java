package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.run.variant.OutputVariantsStartingInIntervalsStep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VariantProcessingRemoteMergeTask extends WorkDirectoryTask<VariantProcessingRemoteMergeTask.Factory>
{
    private static final String ACTION_NAME = "Merging VCFs";

    protected VariantProcessingRemoteMergeTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(VariantProcessingRemoteMergeTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            allowableNames.add(ACTION_NAME);

            return allowableNames;
        }

        @Override
        public boolean isJoin()
        {
            return true;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (job instanceof VariantProcessingJob)
            {
                if (!((VariantProcessingJob)job).isScatterJob())
                {
                    job.getLogger().info("skipping VCF merge task");
                    return false;
                }
            }

            return super.isParticipant(job);
        }

        @Override
        public PipelineJob.Task<?> createTask(PipelineJob job)
        {
            return new VariantProcessingRemoteMergeTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private VariantProcessingJob getPipelineJob()
    {
        return (VariantProcessingJob)getJob();
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper.logModuleVersions(getJob().getLogger());
        RecordedAction action = new RecordedAction(ACTION_NAME);
        TaskFileManagerImpl manager = new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd);
        JobContextImpl ctx = new JobContextImpl(getPipelineJob(), getPipelineJob().getSequenceSupport(), getPipelineJob().getParameterJson(), _wd.getDir(), new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd), _wd);

        File finalOut;
        SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler = getPipelineJob().getHandler();
        if (handler instanceof SequenceOutputHandler.HasCustomVariantMerge)
        {
            finalOut = ((SequenceOutputHandler.HasCustomVariantMerge)handler).performVariantMerge(manager, action, handler, getJob());
        }
        else
        {
            finalOut = runDefaultVariantMerge(ctx, manager, action, handler);
        }

        Map<String, File> scatterOutputs = getPipelineJob().getScatterJobOutputs();
        if (handler instanceof SequenceOutputHandler.TracksVCF)
        {
            Set<SequenceOutputFile> outputs = new HashSet<>();
            scatterOutputs.values().forEach(f -> outputs.addAll(getPipelineJob().getOutputsToCreate().stream().filter(x -> f.equals(x.getFile())).collect(Collectors.toSet())));
            getJob().getLogger().debug("Total component outputs created: " + outputs.size());
            getPipelineJob().getOutputsToCreate().removeAll(outputs);
            getJob().getLogger().debug("Total SequenceOutputFiles on job after remove: " + getPipelineJob().getOutputsToCreate().size());

            SequenceOutputFile finalOutput = ((SequenceOutputHandler.TracksVCF)getPipelineJob().getHandler()).createFinalSequenceOutput(getJob(), finalOut, getPipelineJob().getFiles());
            manager.addSequenceOutput(finalOutput);
        }
        else
        {
            throw new PipelineJobException("Handler does not support TracksVCF: " + handler.getName());
        }

        File cacheDir = getPipelineJob().getLocationForCachedInputs(_wd, false);
        if (cacheDir.exists())
        {
            manager.addIntermediateFile(cacheDir);
        }

        manager.deleteIntermediateFiles();
        manager.cleanup(Collections.singleton(action));

        return new RecordedActionSet(action);
    }

    private File runDefaultVariantMerge(JobContextImpl ctx, TaskFileManagerImpl manager, RecordedAction action, SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler) throws PipelineJobException
    {
        Map<String, List<Interval>> jobToIntervalMap = getPipelineJob().getJobToIntervalMap();
        getJob().setStatus(PipelineJob.TaskStatus.running, "Combining Per-Contig VCFs: " + jobToIntervalMap.size());

        Map<String, File> scatterOutputs = getPipelineJob().getScatterJobOutputs();
        List<File> toConcat = new ArrayList<>();
        Set<File> missing = new HashSet<>();
        for (String name : jobToIntervalMap.keySet())
        {
            if (!scatterOutputs.containsKey(name))
            {
                throw new PipelineJobException("Missing VCF for interval/contig: " + name);
            }

            File vcf = scatterOutputs.get(name);
            if (!vcf.exists())
            {
                missing.add(vcf);
            }

            // NOTE: this was added to fix a one-time issue where -L was dropped from some upstream GenotypeGVCFs.
            // Under normal conditions this would never be necessary.
            boolean ensureOutputsWithinIntervals = getPipelineJob().getParameterJson().optBoolean("variantCalling.GenotypeGVCFs.ensureOutputsWithinIntervalsOnMerge", false);
            if (ensureOutputsWithinIntervals)
            {
                getJob().getLogger().debug("Ensuring ensure scatter outputs respect intervals");

                File subsetVcf = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".subset.vcf.gz");
                File subsetVcfIdx = new File(subsetVcf.getPath() + ".tbi");
                manager.addIntermediateFile(subsetVcf);
                manager.addIntermediateFile(subsetVcfIdx);

                if (subsetVcfIdx.exists())
                {
                    getJob().getLogger().debug("Index exists, will not re-subset the VCF: " + subsetVcf.getName());
                }
                else
                {
                    OutputVariantsStartingInIntervalsStep.Wrapper wrapper = new OutputVariantsStartingInIntervalsStep.Wrapper(getJob().getLogger());
                    wrapper.execute(vcf, subsetVcf, getPipelineJob().getIntervalsForTask());
                }

                toConcat.add(subsetVcf);
            }
            else
            {
                toConcat.add(vcf);
            }

            manager.addInput(action, "Input VCF", vcf);
            manager.addIntermediateFile(vcf);
            manager.addIntermediateFile(new File(vcf.getPath() + ".tbi"));
        }

        Set<Integer> genomeIds = new HashSet<>();
        getPipelineJob().getFiles().forEach(x -> genomeIds.add(x.getLibrary_id()));
        if (genomeIds.size() != 1)
        {
            throw new PipelineJobException("Expected a single genome, found: " + StringUtils.join(genomeIds, ", "));
        }

        ReferenceGenome genome = getPipelineJob().getSequenceSupport().getCachedGenome(genomeIds.iterator().next());

        String basename = SequenceAnalysisService.get().getUnzippedBaseName(toConcat.get(0).getName());
        File combined = new File(getPipelineJob().getAnalysisDirectory(), basename + ".vcf.gz");
        File combinedIdx = new File(combined.getPath() + ".tbi");
        if (combinedIdx.exists())
        {
            getJob().getLogger().info("VCF exists, will not recreate: " + combined.getPath());
        }
        else
        {
            if (!missing.isEmpty())
            {
                throw new PipelineJobException("Missing one of more VCFs: " + missing.stream().map(File::getPath).collect(Collectors.joining(",")));
            }

            boolean sortAfterMerge = handler instanceof VariantProcessingStep.SupportsScatterGather && ((VariantProcessingStep.SupportsScatterGather)handler).doSortAfterMerge();
            combined = SequenceAnalysisService.get().combineVcfs(toConcat, combined, genome, getJob().getLogger(), true, null, sortAfterMerge);
        }
        manager.addOutput(action, "Merged VCF", combined);

        if (handler instanceof VariantProcessingStep.SupportsScatterGather)
        {
            ctx.getLogger().debug("Running additional merge tasks");
            ((VariantProcessingStep.SupportsScatterGather) handler).performAdditionalMergeTasks(ctx, getPipelineJob(), manager, genome, toConcat);
        }

        return combined;
    }
}
