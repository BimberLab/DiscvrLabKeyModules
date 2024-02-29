package org.labkey.singlecell.pipeline.singlecell;

import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.run.CellRangerGexCountStep;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VireoHandler  extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public VireoHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Run Vireo", "This will run cellsnp-lite and vireo to infer cell-to-sample based on genotype.", null, Arrays.asList(
                ToolParameterDescriptor.create("nDonors", "# Donors", "The number of donors to demultiplex", "ldk-integerfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("contigs", "Allowable Contigs", "A comma-separated list of contig names to use", "textfield", new JSONObject(){{

                }}, "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20")
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
        return CellRangerGexCountStep.LOUPE_CATEGORY.equals(o.getCategory()) & o.getFile().getName().endsWith("cloupe.cloupe");
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

    public class Processor implements SequenceOutputProcessor
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
        }

        private File getBarcodesFile(File loupe)
        {
            return new File(loupe.getParentFile(), "filtered_feature_bc_matrix/barcodes.tsv.gz");
        }

        private File getBamFile(File loupe)
        {
            File[] files = loupe.getParentFile().getParentFile().listFiles(f -> f.getName().endsWith(".bam"));
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

            File barcodes = new File(ctx.getWorkingDirectory(), "barcodes.csv");
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

            List<String> cellsnp = new ArrayList<>();
            cellsnp.add("cellsnp-lite");
            cellsnp.add("-s");
            cellsnp.add(bam.getPath());
            cellsnp.add("-b");
            cellsnp.add(barcodes.getPath());

            File cellsnpDir = new File(ctx.getWorkingDirectory(), "cellsnp");
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

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            if (maxThreads != null)
            {
                cellsnp.add("-p");
                cellsnp.add(maxThreads.toString());
            }

            cellsnp.add("--minMAF");
            cellsnp.add("0.1");

            cellsnp.add("--minCOUNT");
            cellsnp.add("100");

            cellsnp.add("--gzip");

            ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(inputFiles.get(0).getLibrary_id());

            cellsnp.add("--refseq");
            cellsnp.add(genome.getWorkingFastaFile().getPath());

            String contigs = ctx.getParams().optString("contigs", "");
            if (!StringUtils.isEmpty(contigs))
            {
                cellsnp.add("--chrom");
                cellsnp.add(contigs);
            }

            new SimpleScriptWrapper(ctx.getLogger()).execute(cellsnp);

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

            int nDonors = ctx.getParams().optInt("nDonors", 0);
            if (nDonors == 0)
            {
                throw new PipelineJobException("Must provide nDonors");
            }

            vireo.add("-N");
            vireo.add(String.valueOf(nDonors));

            new SimpleScriptWrapper(ctx.getLogger()).execute(vireo);

            File[] outFiles = ctx.getWorkingDirectory().listFiles(f -> f.getName().endsWith("_donor_ids.tsv"));
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
            ctx.addSequenceOutput(so);
        }
    }
}
