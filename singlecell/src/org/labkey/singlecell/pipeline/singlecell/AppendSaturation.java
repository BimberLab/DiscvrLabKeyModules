package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.SingleCellSchema;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.singlecell.analysis.ProcessSingleCellHandler.LOUPE_TYPE;

public class AppendSaturation extends AbstractCellMembraneStep
{
    public AppendSaturation(PipelineContext ctx, AppendSaturation.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendSaturation", "Append Saturation", "CellMembrane", "This will calculate sequencing saturation per-cell and append as metadata.", List.of(), null, null);
        }

        @Override
        public AppendSaturation create(PipelineContext ctx)
        {
            return new AppendSaturation(ctx, this);
        }
    }

    @Override
    public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(getMolInfoTable(ctx))))
        {
            Map<Integer, SequenceOutputFile> loupeOutputs = new HashMap<>();
            for (SequenceOutputFile so : inputFiles)
            {
                if (!LOUPE_TYPE.isType(so.getFile()))
                {
                    File meta = new File(so.getFile().getPath().replaceAll(".seurat.rds", ".cellBarcodes.csv"));
                    if (!meta.exists())
                    {
                        throw new PipelineJobException("Cannot find expected metadata file: " + meta.getPath());
                    }

                    Set<Integer> uniqueIds = new HashSet<>();
                    try (CSVReader reader = new CSVReader(Readers.getReader(meta), '_'))
                    {
                        String[] line;
                        while ((line = reader.readNext()) != null)
                        {
                            if (line.length != 2)
                            {
                                throw new PipelineJobException("Unexpected barcode line: " + StringUtils.join(line, "_"));
                            }

                            try
                            {
                                uniqueIds.add(Integer.parseInt(line[0]));
                            }
                            catch (NumberFormatException e)
                            {
                                throw new PipelineJobException("Non-numeric barcode prefix: " + StringUtils.join(line, "_"));
                            }
                        }
                    }

                    for (Integer rowId : uniqueIds)
                    {
                        SequenceOutputFile loupeObj = SequenceOutputFile.getForId(rowId);
                        if (loupeObj == null)
                        {
                            throw new PipelineJobException("Unable to find loupe output file with ID: " + rowId);
                        }
                        loupeOutputs.put(rowId, loupeObj);
                    }
                }
                else
                {
                    loupeOutputs.put(so.getRowid(), so);
                }
            }

            for (Integer rowId : loupeOutputs.keySet())
            {
                SequenceOutputFile loupeFile = loupeOutputs.get(rowId);
                File molInfo = new File(loupeFile.getFile().getParentFile(), "molecule_info.h5");
                if (!molInfo.exists())
                {
                    throw new PipelineJobException("Cannot find file: " + molInfo.getPath());
                }

                if (loupeFile.getReadset() == null)
                {
                    throw new PipelineJobException("Loupe file lacks a readset: " + rowId);
                }

                writer.writeNext(new String[]{String.valueOf(loupeFile.getRowid()), molInfo.getPath(), "RNA"});

                findAdditionalData(loupeFile, writer, ctx.getJob());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getMolInfoTable(SequenceOutputHandler.JobContext ctx)
    {
        return new File(ctx.getSourceDirectory(), "molInfo.txt");
    }

    @Override
    protected List<Chunk> getChunks(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        List<String> lines = new ArrayList<>();

        lines.add("molInfoFiles <- list(");
        File table = getMolInfoTable(ctx);
        if (!table.exists())
        {
            throw new PipelineJobException("Unable to find molInfo table: " + table.getPath());
        }

        try (CSVReader reader = new CSVReader(Readers.getReader(table)))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                File source = new File(line[1]);
                String assay = line[2];
                File dest = new File(ctx.getWorkingDirectory(), line[0] + "." + assay + ".molInfo.h5");
                if (dest.exists())
                {
                    dest.delete();
                }
                FileUtils.copyFile(source, dest);

                lines.add("\t'" + line[0] + "-" + assay + "' = '" + dest.getName() + "',");
                ctx.getFileManager().addIntermediateFile(dest);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        // Remove trailing comma:
        int lastIdx = lines.size() - 1;
        lines.set(lastIdx, lines.get(lastIdx).replaceAll(",$", ""));

        lines.add(")");
        lines.add("");

        List<Chunk> ret = new ArrayList<>();
        ret.add(new Chunk("molInfoFiles", null, null, lines, null));
        ret.addAll(super.getChunks(ctx));

        return ret;
    }

    @Override
    public String getFileSuffix()
    {
        return "saturation";
    }

    private void findAdditionalData(SequenceOutputFile loupeFile, CSVWriter writer, PipelineJob job) throws IOException, PipelineJobException
    {
        Set<Integer> citeReadsets = new HashSet<>();
        Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        TableSelector ts = new TableSelector(QueryService.get().getUserSchema(job.getUser(), targetContainer, SingleCellSchema.NAME).getTable(SingleCellSchema.TABLE_CDNAS), PageFlowUtil.set("hashingReadsetId", "citeseqReadsetId"), new SimpleFilter(FieldKey.fromString("readsetId"), loupeFile.getReadset()), null);
        ts.forEachResults(rs -> {
            if (rs.getObject(FieldKey.fromString("citeseqReadsetId")) != null)
            {
                citeReadsets.add(rs.getInt(FieldKey.fromString("citeseqReadsetId")));
            }
        });

        if (citeReadsets.size() > 1)
        {
            throw new PipelineJobException("More than one CITE-seq readset associated with GEX readset: " + loupeFile.getReadset());
        }
        else if (citeReadsets.size() == 1)
        {
            writeExtraData(loupeFile.getRowid(), citeReadsets.iterator().next(), job, "CITE-seq Counts", writer, "ADT");
        }
    }

    private void writeExtraData(int datasetId, int readsetId, PipelineJob job, String category, CSVWriter writer, String assayName) throws PipelineJobException
    {
        Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), readsetId);
        filter.addCondition(FieldKey.fromString("category"), category);

        List<Integer> rowIds = new TableSelector(QueryService.get().getUserSchema(job.getUser(), targetContainer, SingleCellSchema.SEQUENCE_SCHEMA_NAME).getTable("outputfiles"), PageFlowUtil.set("rowid"), filter, new Sort("-rowid")).getArrayList(Integer.class);
        if (!rowIds.isEmpty())
        {
            if (rowIds.size() >  1)
            {
                job.getLogger().info("More than one " + assayName + " output found for " + readsetId + ", using the most recent: " + rowIds.get(0));
            }

            File molInfo = new File(SequenceOutputFile.getForId(rowIds.get(0)).getFile().getParentFile().getParentFile(), "molecule_info.h5");
            if (!molInfo.exists())
            {
                throw new PipelineJobException("Cannot find file: " + molInfo.getPath());
            }

            writer.writeNext(new String[]{String.valueOf(datasetId), molInfo.getPath(), assayName});
        }
    }
}
