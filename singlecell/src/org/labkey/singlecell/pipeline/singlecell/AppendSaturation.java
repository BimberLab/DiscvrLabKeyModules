package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            super("AppendSaturation", "Append Saturation", "CellMembrane", "This will calculate sequencing saturation per-cell and append as metadata.", Arrays.asList(

            ), null, null);
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
            for (SequenceOutputFile so : inputFiles)
            {
                if (!LOUPE_TYPE.isType(so.getFile()))
                {
                    throw new PipelineJobException("All input files must be loupe files to use sequence saturation");
                }

                File molInfo = new File(so.getFile().getParentFile(), "molecule_info.h5");
                if (!molInfo.exists())
                {
                    throw new PipelineJobException("Cannot find file: " + molInfo.getPath());
                }

                writer.writeNext(new String[]{String.valueOf(so.getRowid()), molInfo.getPath()});
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
                File dest = new File(ctx.getWorkingDirectory(), line[0] + ".molInfo.h5");
                if (dest.exists())
                {
                    dest.delete();
                }
                FileUtils.copyFile(source, dest);

                lines.add("\t'" + line[0] + "' = '" + dest.getName() + "',");
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
}
