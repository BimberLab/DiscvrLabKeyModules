package org.labkey.sequenceanalysis.run.alignment;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.DockerWrapper;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ParagraphStep extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public ParagraphStep()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Paragraph SV Genotyping", "This will run paraGRAPH on one or more BAM files to genotype SVs", null, Arrays.asList(
                ToolParameterDescriptor.createExpDataParam("svVCF", "Input VCF", "This is the DataId of the VCF containing the SVs to genotype", "ldk-expdatafield", new JSONObject()
                {{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("doBndSubset", "Filter Input VCF", "If selected, prior to running SelectVariants will be run to remove BNDs sites with POS<150 and symbolic INS without ALT sequence", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("verbose", "Verbose Logging", "If checked, --verbose will be passed to paragraph to increase logging", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("useLocalScratch", "User local scratch", "If checked, the tool will write the intermediate temp files to a folder in the working directory, rather than the job's tempDir. This can make debugging easier.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("retrieveReferenceSeq", "Retrieve Reference Sequence", "If checked, --debug will be passed to paragraph to increase logging", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false)
        ));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bamOrCram.getFileType().isType(o.getFile());
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            int svVcfId = ctx.getParams().optInt("svVCF", 0);
            if (svVcfId == 0)
            {
                throw new PipelineJobException("svVCF param was null");
            }

            File svVcf = ctx.getSequenceSupport().getCachedData(svVcfId);
            if (svVcf == null)
            {
                throw new PipelineJobException("File not found for ID: " + svVcfId);
            }
            else if (!svVcf.exists())
            {
                throw new PipelineJobException("Missing file: " + svVcf.getPath());
            }

            boolean doBndSubset = ctx.getParams().optBoolean("doBndSubset", false);
            if (doBndSubset)
            {
                File vcfNoBnd = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(svVcf.getName()) + "pgSubset.vcf.gz");
                File vcfNoBndIdx = new File(vcfNoBnd.getPath() + ".tbi");
                if (vcfNoBndIdx.exists())
                {
                    ctx.getLogger().debug("Index exists, will no repeat VCF subset");
                }
                else
                {
                    SelectVariantsWrapper svw = new SelectVariantsWrapper(ctx.getLogger());
                    List<String> selectArgs = new ArrayList<>();
                    selectArgs.add("-select");
                    selectArgs.add("SVTYPE != 'BND' && SVTYPE != 'DUP' && POS > 150 && !(vc.hasAttribute('SVTYPE') && vc.getAttribute('SVTYPE') == 'INS' && vc.hasSymbolicAlleles() && !vc.hasAttribute('SEQ'))");
                    selectArgs.add("--exclude-filtered");
                    selectArgs.add("--exclude-filtered");
                    selectArgs.add("--sites-only-vcf-output");

                    svw.execute(ctx.getSequenceSupport().getCachedGenome(inputFiles.get(0).getLibrary_id()).getWorkingFastaFile(), svVcf, vcfNoBnd, selectArgs);

                    ctx.getFileManager().addIntermediateFile(vcfNoBnd);
                    ctx.getFileManager().addIntermediateFile(vcfNoBndIdx);
                    svVcf = vcfNoBnd;
                }
            }

            Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            for (SequenceOutputFile so : inputFiles)
            {
                List<String> depthArgs = new ArrayList<>();
                depthArgs.add("idxdepth");
                depthArgs.add("-b");
                depthArgs.add(so.getFile().getPath());

                File coverageJson = new File(ctx.getWorkingDirectory(), "coverage.json");
                depthArgs.add("-o");
                depthArgs.add(coverageJson.getPath());

                depthArgs.add("-r");
                depthArgs.add(ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()).getWorkingFastaFile().getPath());

                if (threads != null)
                {
                    depthArgs.add("--threads");
                    depthArgs.add(threads.toString());
                }

                File doneFile = new File(ctx.getWorkingDirectory(), "idxdepth.done");
                ctx.getFileManager().addIntermediateFile(doneFile);
                if (doneFile.exists())
                {
                    ctx.getLogger().info("idxdepth already performed, skipping");
                }
                else
                {
                    new SimpleScriptWrapper(ctx.getLogger()).execute(depthArgs);
                    try
                    {
                        FileUtils.touch(doneFile);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                if (!coverageJson.exists())
                {
                    throw new PipelineJobException("Missing file: " + coverageJson.getPath());
                }
                ctx.getFileManager().addIntermediateFile(coverageJson);

                // Should produce a simple text file:
                //    id  path    depth   read length
                //    IB18  ../IB18.cram 29.77   150
                File coverageFile = new File(ctx.getWorkingDirectory(), "coverage.txt");
                String rgId;
                try (PrintWriter writer = PrintWriters.getPrintWriter(coverageFile); SamReader reader = SamReaderFactory.makeDefault().open(so.getFile()))
                {
                    SAMFileHeader header = reader.getFileHeader();
                    if (header.getReadGroups().isEmpty())
                    {
                        throw new PipelineJobException("No read groups found in input BAM");
                    }

                    Set<String> uniqueSamples = header.getReadGroups().stream().map(SAMReadGroupRecord::getSample).collect(Collectors.toSet());
                    if (uniqueSamples.size() > 1)
                    {
                        throw new PipelineJobException("Readgroups contained more than one unique sample");
                    }

                    rgId = uniqueSamples.iterator().next();

                    JSONObject json = new JSONObject(FileUtils.readFileToString(coverageJson, Charset.defaultCharset()));
                    writer.println("id\tpath\tdepth\tread length");
                    double depth = json.getJSONObject("autosome").getDouble("depth");
                    if (depth <= 0)
                    {
                        throw new PipelineJobException("Depth was zero for file: " + so.getFile().getPath());
                    }

                    double readLength = json.getInt("read_length");
                    writer.println(rgId + "\t" + so.getFile().getPath() + "\t" + depth + "\t" + readLength);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
                ctx.getFileManager().addIntermediateFile(coverageFile);

                DockerWrapper dockerWrapper = new DockerWrapper("ghcr.io/bimberlabinternal/paragraph:latest", ctx.getLogger(), ctx);
                dockerWrapper.setTmpDir(new File(SequencePipelineService.get().getJavaTempDir()));

                List<String> paragraphArgs = new ArrayList<>();
                paragraphArgs.add("/opt/paragraph/bin/multigrmpy.py");

                File paragraphOutDir = new File(ctx.getWorkingDirectory(), FileUtil.getBaseName(so.getFile()));
                paragraphArgs.add("-o");
                paragraphArgs.add(paragraphOutDir.getPath());

                File localScratchDir = new File(ctx.getOutputDir(), "pgScratch");
                if (localScratchDir.exists())
                {
                    try
                    {
                        FileUtils.deleteDirectory(localScratchDir);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                boolean useLocalScratch = ctx.getParams().optBoolean("useLocalScratch", false);
                if (useLocalScratch)
                {
                    paragraphArgs.add("--scratch-dir");
                    paragraphArgs.add(localScratchDir.getPath());

                    ctx.getFileManager().addIntermediateFile(localScratchDir);
                }

                paragraphArgs.add("-i");
                paragraphArgs.add(svVcf.getPath());

                paragraphArgs.add("-m");
                paragraphArgs.add(coverageFile.getPath());

                if (ctx.getParams().optBoolean("verbose", false))
                {
                    paragraphArgs.add("--verbose");
                }

                paragraphArgs.add("-r");
                File genomeFasta = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id()).getWorkingFastaFile();
                paragraphArgs.add(genomeFasta.getPath());

                if (ctx.getParams().optBoolean("retrieveReferenceSeq", false))
                {
                    paragraphArgs.add("--retrieve-reference-sequence");
                }

                if (threads != null)
                {
                    paragraphArgs.add("--threads");
                    paragraphArgs.add(threads.toString());
                }

                dockerWrapper.executeWithDocker(paragraphArgs, ctx.getWorkingDirectory(), ctx.getFileManager(), Arrays.asList(so.getFile(), genomeFasta, svVcf));

                File genotypes = new File(paragraphOutDir, "genotypes.vcf.gz");
                if (!genotypes.exists())
                {
                    throw new PipelineJobException("Missing file: " + genotypes.getPath());
                }

                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(genotypes, ctx.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                ctx.getFileManager().addSequenceOutput(genotypes, "paraGRAPH Genotypes: " + so.getName(), "paraGRAPH Genoypes", so.getReadset(), null, so.getLibrary_id(), "Input VCF: " + svVcf.getName() + " (" + svVcfId + ")");

                ctx.getFileManager().addIntermediateFile(new File(paragraphOutDir, "variants.json.gz"));
                ctx.getFileManager().addIntermediateFile(new File(paragraphOutDir, "variants.vcf.gz"));
                ctx.getFileManager().addIntermediateFile(new File(paragraphOutDir, "genotypes.json.gz"));
                ctx.getFileManager().addIntermediateFile(new File(paragraphOutDir, "grmpy.log"));
            }
        }
    }
}