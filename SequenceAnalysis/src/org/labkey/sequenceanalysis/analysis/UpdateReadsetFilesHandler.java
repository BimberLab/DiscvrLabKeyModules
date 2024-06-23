package org.labkey.sequenceanalysis.analysis;

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

            ctx.getSequenceSupport().cacheObject("readsetId", newRsName);
        }

        private SAMFileHeader getAndValidateHeaderForBam(SequenceOutputFile so, String newRsName) throws PipelineJobException
        {
            SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
            try (SamReader reader = samReaderFactory.open(so.getFile()))
            {
                SAMFileHeader header = reader.getFileHeader().clone();
                int nSamples = reader.getFileHeader().getReadGroups().size();
                if (nSamples != 1)
                {
                    throw new PipelineJobException("File has more than one read group, found: " + nSamples);
                }

                List<SAMReadGroupRecord> rgs = header.getReadGroups();
                String existingSample = rgs.get(0).getSample();
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
                FileUtils.moveFile(outputVcf, so.getFile(), StandardCopyOption.REPLACE_EXISTING);

                FileType gz = new FileType(".gz");
                File inputIndex = gz.isType(so.getFile()) ? new File(so.getFile().getPath() + ".tbi") : new File(so.getFile().getPath() + FileExtensions.TRIBBLE_INDEX);
                FileUtils.moveFile(outputIdx, inputIndex, StandardCopyOption.REPLACE_EXISTING);

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
                rgs.get(0).setSample(newRsName);

                File headerBam = new File(ctx.getWorkingDirectory(), "header.bam");
                try (SAMFileWriter writer = new SAMFileWriterFactory().makeBAMWriter(header, false, headerBam))
                {

                }
                ctx.getFileManager().addIntermediateFile(headerBam);
                ctx.getFileManager().addIntermediateFile(SequencePipelineService.get().getExpectedIndex(headerBam));

                File output = new File(ctx.getWorkingDirectory(), so.getFile().getName());
                new ReplaceSamHeaderWrapper(ctx.getLogger()).execute(so.getFile(), output, headerBam);
                if (!output.exists())
                {
                    throw new PipelineJobException("Missing file: " + output.getPath());
                }

                File outputIdx = SequencePipelineService.get().ensureBamIndex(output, ctx.getLogger(), false);

                FileUtils.moveFile(output, so.getFile(), StandardCopyOption.REPLACE_EXISTING);
                FileUtils.moveFile(outputIdx, SequenceAnalysisService.get().getExpectedBamOrCramIndex(so.getFile()), StandardCopyOption.REPLACE_EXISTING);

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

            public void execute(File input, File output, File headerBam) throws PipelineJobException
            {
                List<String> params = new ArrayList<>(getBaseArgs());

                params.add("--INPUT");
                params.add(input.getPath());

                params.add("--OUTPUT");
                params.add(output.getPath());

                params.add("--HEADER");
                params.add(headerBam.getPath());

                execute(params);
            }
        }
    }
}