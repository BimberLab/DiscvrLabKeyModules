package org.labkey.singlecell.run;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMFileHeader;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class VelocytoAlignmentStep extends AbstractCellRangerDependentStep
{
    public VelocytoAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, CellRangerWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("velocyto", "This will run velocyto to generate a supplemental feature count matrix", getCellRangerGexParams(Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("mask", "Mask File", "This is the ID of an optional GTF file containing repetitive regions to mask.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam("samtoolsMem", "Samtools Mem To Sort (GB)", "The amount of ram to use to samtools sort", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 10)
            )), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js")), null, true, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new VelocytoAlignmentStep(this, context, new CellRangerWrapper(context.getLogger()));
        }
    }

    @Override
    public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        AlignmentOutputImpl output = new AlignmentOutputImpl();
        File localBam = runCellRanger(output, rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);

        File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("gtfFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
        if (gtf == null)
        {
            throw new PipelineJobException("Missing GTF file param");
        }
        else if (!gtf.exists())
        {
            throw new PipelineJobException("File not found: " + gtf.getPath());
        }

        File mask = null;
        if (getProvider().getParameterByName("mask").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class) != null)
        {
            mask = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("mask").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
            if (!mask.exists())
            {
                throw new PipelineJobException("Missing file: " + mask.getPath());
            }
        }

        Integer samtoolsMem = getProvider().getParameterByName("samtoolsMem").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File loom = new VelocytoWrapper(getPipelineCtx().getLogger()).runVelocytoFor10x(localBam, gtf, outputDirectory, mask, rs, samtoolsMem);
        output.addSequenceOutput(loom, rs.getName() + ": velocyto", "Velocyto Counts", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return super.createIndex(referenceGenome, outputDir);
    }

    public static class VelocytoWrapper extends AbstractCommandWrapper
    {
        public VelocytoWrapper(Logger log)
        {
            super(log);
        }

        public File runVelocytoFor10x(File localBam, File gtf, File outputFolder, @Nullable File mask, Readset rs, @Nullable Integer samtoolsMem) throws PipelineJobException
        {
            getLogger().debug("Inspecting GTF for lines without gene_id or transcript_id");
            int linesDropped = 0;
            File gtfEdit = new File(outputFolder, FileUtil.getBaseName(gtf) + ".geneId.gtf");
            try (CSVReader reader = new CSVReader(Readers.getReader(gtf), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(gtfEdit), '\t', CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    //Drop lines lacking gene_id/transcript, or with empty gene_id:
                    if (!line[0].startsWith("#") && (!line[8].contains("gene_id") || !line[8].contains("transcript_id") || line[8].contains("gene_id \"\"") || line[8].contains("transcript_id \"\"")))
                    {
                        linesDropped++;
                        continue;
                    }

                    writer.writeNext(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (linesDropped == 0)
            {
                getLogger().debug("No GTF lines were invalid, using original");
                gtfEdit.delete();
            }
            else
            {
                getLogger().info("dropped " + linesDropped + " lines lacking gene_id, transcript_id, or with an empty value for gene_id/transcript_id");
                gtf = gtfEdit;
            }

            // https://velocyto.org/velocyto.py/tutorial/cli.html#run10x-run-on-10x-chromium-samples
            // velocyto run10x -m repeat_msk.gtf mypath/sample01 somepath/refdata-cellranger-mm10-1.2.0/genes/genes.gtf
            // velocyto run -b filtered_barcodes.tsv -o output_path -m repeat_msk_srt.gtf possorted_genome_bam.bam mm10_annotation.gtf

            getLogger().debug("Using BAM: " + localBam.getPath());

            //Pre-sort since velocyto dies when doing this itself:
            //samtools ``sort -l [compression] -m [mb_to_use]M -t [tagname] -O BAM -@ [threads_to_use] -o cellsorted_[bamfile] [bamfile]
            List<String> sortArgs = Arrays.asList("-t", "CB");
            File sortedBam = new File(localBam.getParentFile(), "cellsorted_" + localBam.getName());
            new SamSorter(getLogger()).execute(localBam, sortedBam, SAMFileHeader.SortOrder.unknown, sortArgs);


            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getLogger());
            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getExeForPackage("VELOCYTOPATH", "velocyto").getPath());
            args.add("run");

            args.add("-o");
            args.add(outputFolder.getPath());

            args.add("-b");
            String sampleName = CellRangerWrapper.makeLegalSampleName(rs.getName());

            // NOTE: depends on whether this is run immediately after cellranger or not, the BAM might exist in ./outs or might have been moved top-level:
            File barcodeCSV = new File(localBam.getParentFile(), (localBam.getParentFile().getName().equals("outs") ? "" : sampleName + "/outs/") + "raw_feature_bc_matrix/barcodes.tsv.gz");
            if (!barcodeCSV.exists())
            {
                throw new PipelineJobException("Unable to find file: " + barcodeCSV.getPath());
            }
            args.add(barcodeCSV.getPath());

            Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (threads != null && threads >= 4)
            {
                args.add("--samtools-threads");
                // Cap this at 4 due to memory
                args.add(String.valueOf(Math.min(threads - 2, 4)));
            }

            if (samtoolsMem != null)
            {
                // velocyto expects this in Mb
                args.add("--samtools-memory");
                args.add(String.valueOf(samtoolsMem * 1000));
            }

            if (mask != null)
            {
                args.add("-m");
                args.add(mask.getPath());
            }

            args.add(localBam.getPath());
            args.add(gtf.getPath());

            wrapper.execute(args);

            if (gtfEdit.exists())
            {
                gtfEdit.delete();
            }

            File loom = new File(outputFolder, sampleName + ".loom");
            if (!loom.exists())
            {
                throw new PipelineJobException("Missing expected file: " + loom.getPath());
            }

            sortedBam.delete();

            return loom;
        }
    }
}
