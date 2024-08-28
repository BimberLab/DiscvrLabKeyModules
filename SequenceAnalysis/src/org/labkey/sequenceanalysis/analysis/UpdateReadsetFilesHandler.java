package org.labkey.sequenceanalysis.analysis;

import com.google.common.io.Files;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFReader;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UpdateReadsetFilesHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public UpdateReadsetFilesHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Update Sample/Header Information", "This will re-header any BAM or gVCF files using the sample name from the source readset. All inputs must be single-sample and have a readset attached to the record", null, List.of(

        ));
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (
                SequenceUtil.FILETYPE.gvcf.getFileType().isType(f.getFile()) ||
                SequenceUtil.FILETYPE.bamOrCram.getFileType().isType(f.getFile())
        );
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (inputFiles.size() > 1)
            {
                throw new PipelineJobException("This job expects a single input file!");
            }

            SequenceOutputFile so = inputFiles.get(0);
            if (so.getReadset() == null)
            {
                throw new PipelineJobException("All inputs must have a readset, missing: " + so.getRowid());
            }

            Readset rs = SequenceAnalysisService.get().getReadset(so.getReadset(), ctx.getJob().getUser());
            String newRsName = SequenceUtil.getLegalReadGroupName(rs.getName());

            if (SequenceUtil.FILETYPE.bamOrCram.getFileType().isType(so.getFile()))
            {
                getAndValidateHeaderForBam(so, newRsName);
            }
            else if (SequenceUtil.FILETYPE.gvcf.getFileType().isType(so.getFile()) | SequenceUtil.FILETYPE.vcf.getFileType().isType(so.getFile()))
            {
                getAndValidateHeaderForVcf(so, newRsName);
            }
            else
            {
                throw new PipelineJobException("Unexpected file type: " + so.getFile().getPath());
            }

            ctx.getSequenceSupport().cacheObject("readsetId", newRsName);
        }

        private SAMFileHeader getAndValidateHeaderForBam(SequenceOutputFile so, String newRsName) throws PipelineJobException
        {
            SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
            try (SamReader reader = samReaderFactory.open(so.getFile()))
            {
                SAMFileHeader header = reader.getFileHeader().clone();
                List<SAMReadGroupRecord> rgs = header.getReadGroups();
                Set<String> distinctLibraries = rgs.stream().map(SAMReadGroupRecord::getLibrary).collect(Collectors.toSet());
                if (distinctLibraries.size() > 1)
                {
                    throw new PipelineJobException("File has more than one library in read group(s), found: " + distinctLibraries.stream().collect(Collectors.joining(", ")));
                }

                Set<String> distinctSamples = rgs.stream().map(SAMReadGroupRecord::getSample).collect(Collectors.toSet());
                if (distinctSamples.size() > 1)
                {
                    throw new PipelineJobException("File has more than one sample in read group(s), found: " + distinctSamples.stream().collect(Collectors.joining(", ")));
                }

                if (
                        distinctLibraries.stream().filter(x -> !x.equals(newRsName)).count() == 0L &&
                        distinctSamples.stream().filter(x -> !x.equals(newRsName)).count() == 0L
                )
                {
                    throw new PipelineJobException("Sample and library names match in read group(s), aborting");
                }

                return header;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private VCFHeader getAndValidateHeaderForVcf(SequenceOutputFile so, String newRsName) throws PipelineJobException
        {
            try (VCFReader reader = new VCFFileReader(so.getFile()))
            {
                VCFHeader header = reader.getHeader();
                int nSamples = header.getGenotypeSamples().size();
                if (nSamples != 1)
                {
                    throw new PipelineJobException("File has more than one sample, found: " + nSamples);
                }

                String existingSample = header.getGenotypeSamples().get(0);
                if (existingSample.equals(newRsName))
                {
                    throw new PipelineJobException("Sample names match, aborting");
                }

                return header;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            String newRsName = ctx.getSequenceSupport().getCachedObject("readsetId", String.class);
            if (newRsName == null)
            {
                throw new PipelineJobException("Missing cached readsetId");
            }

            SequenceOutputFile so = inputFiles.get(0);
            if (SequenceUtil.FILETYPE.bamOrCram.getFileType().isType(so.getFile()))
            {
                reheaderBamOrCram(so, ctx, newRsName);
            }
            else if (SequenceUtil.FILETYPE.gvcf.getFileType().isType(so.getFile()) | SequenceUtil.FILETYPE.vcf.getFileType().isType(so.getFile()))
            {
                reheaderVcf(so, ctx, newRsName);
            }
        }

        private void reheaderVcf(SequenceOutputFile so, JobContext ctx, String newRsName) throws PipelineJobException
        {
            VCFHeader header = getAndValidateHeaderForVcf(so, newRsName);
            String existingSample = header.getGenotypeSamples().get(0);

            File sampleNamesFile =  new File(ctx.getWorkingDirectory(), "sampleNames.txt");
            if (!sampleNamesFile.exists())
            {
                try
                {
                    Files.touch(sampleNamesFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            try (PrintWriter writer = PrintWriters.getPrintWriter(sampleNamesFile, StandardOpenOption.APPEND))
            {
                writer.println(newRsName);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            ctx.getFileManager().addIntermediateFile(sampleNamesFile);

            File outputVcf = new File(ctx.getWorkingDirectory(), so.getFile().getName());

            BcftoolsRunner wrapper = new BcftoolsRunner(ctx.getLogger());
            wrapper.execute(Arrays.asList(BcftoolsRunner.getBcfToolsPath().getPath(), "reheader", "-s", sampleNamesFile.getPath(), "-o", outputVcf.getPath(), so.getFile().getPath()));

            try
            {
                File outputIdx = SequenceAnalysisService.get().ensureVcfIndex(outputVcf, ctx.getLogger(), false);
                if (so.getFile().exists())
                {
                    so.getFile().delete();
                }
                FileUtils.moveFile(outputVcf, so.getFile());

                FileType gz = new FileType(".gz");
                File inputIndex = gz.isType(so.getFile()) ? new File(so.getFile().getPath() + ".tbi") : new File(so.getFile().getPath() + FileExtensions.TRIBBLE_INDEX);
                if (inputIndex.exists())
                {
                    inputIndex.delete();
                }
                FileUtils.moveFile(outputIdx, inputIndex);

                addTracker(so, existingSample, newRsName);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void addTracker(SequenceOutputFile so, String existingSample, String newRsName) throws IOException
        {
            File tracker = new File(so.getFile().getParentFile(), "reheaderHistory.txt");
            boolean preExisting = tracker.exists();
            if (!preExisting)
            {
                Files.touch(tracker);
            }

            try (PrintWriter writer = PrintWriters.getPrintWriter(tracker, StandardOpenOption.APPEND))
            {
                if (!preExisting)
                {
                    writer.println("OriginalSample\tNewSample");
                }

                writer.println(existingSample + "\t" + newRsName);
            }
        }

        private void reheaderBamOrCram(SequenceOutputFile so, JobContext ctx, String newRsName) throws PipelineJobException
        {
            try
            {
                SAMFileHeader header = getAndValidateHeaderForBam(so, newRsName);

                List<SAMReadGroupRecord> rgs = header.getReadGroups();
                String existingSample = rgs.get(0).getSample();
                String existingLibrary = rgs.get(0).getLibrary();
                rgs.forEach(rg -> {
                    rg.setSample(newRsName);
                    rg.setLibrary(newRsName);
                });

                File headerBam = new File(ctx.getWorkingDirectory(), "header.bam");
                try (SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(header, false, headerBam))
                {

                }

                if (!headerBam.exists())
                {
                    throw new PipelineJobException("Expected header was not created: " + headerBam.getPath());
                }

                ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                if (rg == null)
                {
                    throw new PipelineJobException("Unable to find genome: " + so.getLibrary_id());
                }

                ctx.getFileManager().addIntermediateFile(headerBam);
                ctx.getFileManager().addIntermediateFile(SequencePipelineService.get().getExpectedIndex(headerBam));

                File output = new File(ctx.getWorkingDirectory(), so.getFile().getName());
                new ReplaceSamHeaderWrapper(ctx.getLogger()).execute(so.getFile(), output, headerBam, rg);
                if (!output.exists())
                {
                    throw new PipelineJobException("Missing file: " + output.getPath());
                }

                File outputIdx = SequencePipelineService.get().ensureBamIndex(output, ctx.getLogger(), false);

                if (so.getFile().exists())
                {
                    so.getFile().delete();
                }
                FileUtils.moveFile(output, so.getFile());

                File inputIndex = SequenceAnalysisService.get().getExpectedBamOrCramIndex(so.getFile());
                if (inputIndex.exists())
                {
                    inputIndex.delete();
                }
                FileUtils.moveFile(outputIdx, inputIndex);

                addTracker(so, existingSample, newRsName);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private static class ReplaceSamHeaderWrapper extends PicardWrapper
        {
            public ReplaceSamHeaderWrapper(Logger log)
            {
                super(log);
            }

            @Override
            protected String getToolName()
            {
                return "ReplaceSamHeader";
            }

            public void execute(File input, File output, File headerBam, ReferenceGenome genome) throws PipelineJobException
            {
                List<String> params = new ArrayList<>(getBaseArgs());

                params.add("--INPUT");
                params.add(input.getPath());

                params.add("--OUTPUT");
                params.add(output.getPath());

                params.add("--HEADER");
                params.add(headerBam.getPath());

                params.add("-R");
                params.add(genome.getWorkingFastaFile().getPath());

                execute(params);
            }
        }
    }
}