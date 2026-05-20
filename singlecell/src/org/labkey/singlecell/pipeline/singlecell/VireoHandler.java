package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.BcftoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.run.CellRangerGexCountStep;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class VireoHandler  extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private static final String REF_VCF =  "refVCF";
    private static final String SKIP_VCF_FOR_VIREO =  "omitRefVcfForVireo";

    public VireoHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Run CellSnp-Lite/Vireo", "This will run cellsnp-lite and vireo to infer cell-to-sample based on genotype.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/SequenceOutputFileSelectorField.js")), Arrays.asList(
                ToolParameterDescriptor.create("nDonors", "# Donors", "The number of donors to demultiplex. This can be blank only if a reference VCF is provided.", "ldk-integerfield", new JSONObject(){{

                }}, null),
                ToolParameterDescriptor.create("maxDepth", "Max Depth", "At a position, read maximally INT reads per input file, to avoid excessive memory usage", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 0);
                }}, null),
                ToolParameterDescriptor.create("contigs", "Allowable Contigs", "A comma-separated list of contig names to use", "textfield", new JSONObject(){{

                }}, "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20"),
                ToolParameterDescriptor.createExpDataParam(REF_VCF, "Reference SNV Sites", "If provided, these sites will be used to screen for SNPs, instead of discovering them. If provided, the contig list will be ignored", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject()
                {{
                    put("allowBlank", true);
                    put("category", "VCF File");
                    put("performGenomeFilter", false);
                    put("doNotIncludeInTemplates", true);
                }}, null),
                ToolParameterDescriptor.createExpDataParam(SKIP_VCF_FOR_VIREO, "Use Reference VCF for CellSnp-List Only", "If checked, the reference VCF will only be used for cellsnp-lite, not vireo", "checkbox", new JSONObject()
                {{

                }}, false),
                ToolParameterDescriptor.create("storeCellSnpVcf", "Store CellSnp-Lite VCF", "If checked, the cellsnp donor calls VCF will be stored as an output file", "checkbox", null, false)
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
        return CellRangerGexCountStep.LOUPE_CATEGORY.equals(o.getCategory()) && o.getFile() != null && o.getFile().getName().endsWith("cloupe.cloupe");
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
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (inputFiles.size() > 1)
            {
                throw new PipelineJobException("Expected a single input");
            }

            File bc = getBarcodesFile(inputFiles.get(0).getFile());
            if (!bc.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bc.getPath());
            }

            File bam = getBamFile(inputFiles.get(0).getFile());
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bam.getPath());
            }

            int nDonors = ctx.getParams().optInt("nDonors", -1);
            int vcfFile = ctx.getParams().optInt(REF_VCF, -1);
            if (nDonors == -1 & vcfFile == -1)
            {
                throw new PipelineJobException("nDonors must be provided, unless a reference VCF is used");
            }
        }

        private File getBarcodesFile(File loupe)
        {
            return new File(loupe.getParentFile(), "filtered_feature_bc_matrix/barcodes.tsv.gz");
        }

        private File getBamFile(File loupe)
        {
            File[] files = loupe.getParentFile().listFiles(f -> f.getName().endsWith(".bam"));
            if (files == null || files.length == 0)
            {
                throw new IllegalArgumentException("Unable to find BAM file for Loupe file");
            }
            else if (files.length > 1)
            {
                throw new IllegalArgumentException("More than one possible BAM file found");
            }

            return files[0];
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            File barcodesGz = getBarcodesFile(inputFiles.get(0).getFile());
            File bam = getBamFile(inputFiles.get(0).getFile());

            File barcodes = new File(ctx.getWorkingDirectory(), "barcodes.tsv");
            try (BufferedReader reader = IOUtil.openFileForBufferedUtf8Reading(barcodesGz); PrintWriter writer = PrintWriters.getPrintWriter(barcodes))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    writer.println(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            ctx.getFileManager().addIntermediateFile(barcodes);

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(inputFiles.get(0).getLibrary_id());

            File cellsnpDir = new File(ctx.getWorkingDirectory(), "cellsnp");
            File cellsnpVcf = new File(cellsnpDir, "cellSNP.base.vcf.gz");
            File cellsnpVcfIdx = new File(cellsnpVcf.getPath() + ".tbi");
            if (cellsnpVcfIdx.exists())
            {
                ctx.getLogger().info("cellsnp has already run, resuming");
            }
            else
            {
                List<String> cellsnp = new ArrayList<>();
                cellsnp.add("cellsnp-lite");
                cellsnp.add("-s");
                cellsnp.add(bam.getPath());
                cellsnp.add("-b");
                cellsnp.add(barcodes.getPath());
                cellsnp.add("--genotype");

                if (cellsnpDir.exists())
                {
                    try
                    {
                        FileUtils.deleteDirectory(cellsnpDir);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                cellsnp.add("-O");
                cellsnp.add(cellsnpDir.getPath());

                if (maxThreads != null)
                {
                    cellsnp.add("-p");
                    cellsnp.add(maxThreads.toString());
                }

                String maxDepth = StringUtils.trimToNull(ctx.getParams().optString("maxDepth"));
                if (maxDepth != null)
                {
                    cellsnp.add("--maxDEPTH");
                    cellsnp.add(maxDepth);
                }

                cellsnp.add("--gzip");

                cellsnp.add("--refseq");
                cellsnp.add(genome.getWorkingFastaFile().getPath());

                int vcfFile = ctx.getParams().optInt(REF_VCF, -1);
                if (vcfFile > -1)
                {
                    File vcf = ctx.getSequenceSupport().getCachedData(vcfFile);
                    if (vcf == null || !vcf.exists())
                    {
                        throw new PipelineJobException("Unable to find file with ID: " + vcfFile);
                    }

                    cellsnp.add("-R");
                    cellsnp.add(vcf.getPath());
                }
                else
                {
                    String contigs = ctx.getParams().optString("contigs", "");
                    if (!StringUtils.isEmpty(contigs))
                    {
                        cellsnp.add("--chrom");
                        cellsnp.add(contigs);
                    }

                    cellsnp.add("--minMAF");
                    cellsnp.add("0.1");

                    cellsnp.add("--minCOUNT");
                    cellsnp.add("100");
                }

                new SimpleScriptWrapper(ctx.getLogger()).execute(cellsnp);

                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(cellsnpVcf, ctx.getLogger());

                    File cellsVcf = new File(cellsnpDir, "cellSNP.cells.vcf.gz");
                    SequenceAnalysisService.get().ensureVcfIndex(cellsVcf, ctx.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            File cellSnpBaseVcf = new File(cellsnpDir, "cellSNP.base.vcf.gz");
            if (!cellSnpBaseVcf.exists())
            {
                throw new PipelineJobException("Unable to find cellsnp base VCF");
            }


            File cellSnpCellsVcf = new File(cellsnpDir, "cellSNP.cells.vcf.gz");
            if (!cellSnpCellsVcf.exists())
            {
                throw new PipelineJobException("Unable to find cellsnp calls VCF");
            }

            int vcfFile = ctx.getParams().optInt(REF_VCF, -1);
            boolean skipVcfFileForVireo = ctx.getParams().optBoolean(SKIP_VCF_FOR_VIREO, false);

            File refVcfSubset = null;
            if (vcfFile > -1 && !skipVcfFileForVireo)
            {
                File vcf = ctx.getSequenceSupport().getCachedData(vcfFile);
                if (vcf == null || !vcf.exists())
                {
                    throw new PipelineJobException("Unable to find file with ID: " + vcfFile);
                }

                refVcfSubset = new File(ctx.getWorkingDirectory(), vcf.getName());
                BcftoolsRunner bcftoolsRunner = new BcftoolsRunner(ctx.getLogger());
                bcftoolsRunner.execute(Arrays.asList(
                        BcftoolsRunner.getBcfToolsPath().getAbsolutePath(),
                        "view",
                        vcf.getPath(),
                        "-R",
                        cellSnpCellsVcf.getPath(),
                        "-Oz",
                        "-o",
                        refVcfSubset.getPath()
                ));

                ctx.getFileManager().addIntermediateFile(refVcfSubset);
                ctx.getFileManager().addIntermediateFile(new File(refVcfSubset.getPath() + ".tbi"));
            }

            List<String> vireo = new ArrayList<>();
            vireo.add("vireo");
            vireo.add("-c");
            vireo.add(cellsnpDir.getPath());

            if (maxThreads != null)
            {
                vireo.add("-p");
                vireo.add(maxThreads.toString());
            }

            vireo.add("-o");
            vireo.add(ctx.getWorkingDirectory().getPath());

            boolean storeCellSnpVcf = ctx.getParams().optBoolean("storeCellSnpVcf", false);
            if (refVcfSubset != null)
            {
                vireo.add("-d");
                vireo.add(refVcfSubset.getPath());
            }

            // Note: this value should be checked in init(). It is required only if refVCF is null
            int nDonors = ctx.getParams().optInt("nDonors", -1);
            if (nDonors > -1)
            {
                vireo.add("-N");
                vireo.add(String.valueOf(nDonors));
            }

            if (nDonors == 1)
            {
                storeCellSnpVcf = true;
                ctx.getLogger().info("nDonor was 1, skipping vireo");
            }
            else
            {
                new SimpleScriptWrapper(ctx.getLogger()).execute(vireo);

                File[] outFiles = ctx.getWorkingDirectory().listFiles(f -> f.getName().endsWith("donor_ids.tsv"));
                if (outFiles == null || outFiles.length == 0)
                {
                    throw new PipelineJobException("Missing vireo output file");
                }
                else if (outFiles.length > 1)
                {
                    throw new PipelineJobException("More than one possible vireo output file found");
                }

                SequenceOutputFile so = new SequenceOutputFile();
                so.setReadset(inputFiles.get(0).getReadset());
                so.setLibrary_id(inputFiles.get(0).getLibrary_id());
                so.setFile(outFiles[0]);
                if (so.getReadset() != null)
                {
                    so.setName(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName() + ": Vireo Demultiplexing");
                }
                else
                {
                    so.setName(inputFiles.get(0).getName() + ": Vireo Demultiplexing");
                }
                so.setCategory("Vireo Demultiplexing");
                StringBuilder description = new StringBuilder();
                if (vcfFile > -1)
                {
                    description.append("Reference VCF ID: \n").append(vcfFile).append("\n");
                }

                File summary = new File(ctx.getOutputDir(), "summary.tsv");
                if (!summary.exists())
                {
                    throw new PipelineJobException("Missing file: " + summary.getPath());
                }

                description.append("Results:\n");
                try (CSVReader reader = new CSVReader(Readers.getReader(summary), '\t'))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        if ("Var1".equals(line[0]))
                        {
                            continue;
                        }

                        description.append(line[0]).append(": ").append(line[1]).append("\n");
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                so.setDescription(StringUtils.trimToEmpty(description.toString()));
                ctx.addSequenceOutput(so);
            }

            if (storeCellSnpVcf)
            {
                File fixedVcf = sortAndFixVcf(cellSnpBaseVcf, genome, ctx.getLogger(), ctx.getWorkingDirectory());

                SequenceOutputFile so = new SequenceOutputFile();
                so.setReadset(inputFiles.get(0).getReadset());
                so.setLibrary_id(inputFiles.get(0).getLibrary_id());
                so.setFile(fixedVcf);
                if (so.getReadset() != null)
                {
                    so.setName(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName() + ": Cellsnp-lite VCF");
                }
                else
                {
                    so.setName(inputFiles.get(0).getName() + ": Cellsnp-lite Base VCF");
                }
                so.setCategory("VCF File");
                ctx.addSequenceOutput(so);
            }
            else
            {
                ctx.getFileManager().addIntermediateFile(cellSnpBaseVcf.getParentFile());
            }
        }

        private File sortAndFixVcf(File vcf, ReferenceGenome genome, Logger log, File outDir) throws PipelineJobException
        {
            File outVcf = new File(outDir, vcf.getName());

            // This original VCF is generally not properly sorted, and has an invalid index. This is redundant, the VCF is not that large:
            try
            {
                FileUtils.copyFile(vcf, outVcf);
                SequencePipelineService.get().sortROD(outVcf, log, 2);
                SequenceAnalysisService.get().ensureVcfIndex(outVcf, log, true);

                new UpdateVCFSequenceDictionary(log).execute(outVcf, genome.getSequenceDictionary());
                SequenceAnalysisService.get().ensureVcfIndex(outVcf, log);

                return outVcf;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        public static class UpdateVCFSequenceDictionary extends AbstractGatk4Wrapper
        {
            public UpdateVCFSequenceDictionary(Logger log)
            {
                super(log);
            }

            public void execute(File vcf, File dict) throws PipelineJobException
            {
                List<String> args = new ArrayList<>(getBaseArgs("UpdateVCFSequenceDictionary"));
                args.add("-V");
                args.add(vcf.getPath());

                args.add("--source-dictionary");
                args.add(dict.getPath());

                args.add("--replace");
                args.add("true");

                File output = new File(vcf.getParentFile(), "tmp.vcf.gz");
                args.add("-O");
                args.add(output.getPath());

                execute(args);

                if (!output.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + output.getPath());
                }

                try
                {
                    // replace original:
                    vcf.delete();
                    FileUtils.moveFile(output, vcf);

                    File outputIdx = new File(output.getPath() + ".tbi");
                    File vcfIdx = new File(vcf.getPath() + ".tbi");
                    if (vcfIdx.exists())
                    {
                        vcfIdx.delete();
                    }

                    FileUtils.moveFile(outputIdx, vcfIdx);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

            }
        }
    }
}
