package org.labkey.sequenceanalysis.run.preprocessing;

import au.com.bytecode.opencsv.CSVReader;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.BlastNWrapper;
import org.labkey.sequenceanalysis.util.FastqToFastaConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 2/10/2016.
 */
public class BlastFilterPipelineStep extends AbstractPipelineStep implements PreprocessingStep
{
    public BlastFilterPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static String BLAST_DB = "blastDB";
    private static String E_VAL = "eVal";
    private static String EXCLUDE = "exclude";

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("BLASTFilter", "Filter Reads (BLAST)", "BLAST+", "This step provides the ability to filter input reads against a BLAST DB, with the option to either retain or exclude matches, based on a threshold.", Arrays.asList(
                    new BlastDbDescriptor(),
                    ToolParameterDescriptor.create(E_VAL, "E-Value", "Any BLAST hits below this threshold will be ignored (treated as unmatched)", "ldk-numberfield", new JSONObject()
                    {{
                        put("decimalPrecision", 6);
                    }}, 10^-6),
                    ToolParameterDescriptor.create(EXCLUDE, "Exclude Hits?", "If checked, any hits above the expect value will be excluded.  Otherwise, only those reads with a match above the threshold will be retained", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }

        public BlastFilterPipelineStep create(PipelineContext context)
        {
            return new BlastFilterPipelineStep(this, context);
        }
    }

    public static class BlastDbDescriptor extends ToolParameterDescriptor implements ToolParameterDescriptor.CachableParam
    {
        public BlastDbDescriptor()
        {
            super(null, BLAST_DB, "BLAST Database", "The BLAST DB to use", "ldk-simplelabkeycombo", null, new JSONObject()
            {{
                    put("containerPath", 0);
                    put("schemaName", "blast");
                    put("queryName", "databases");
                    put("displayField", "name");
                    put("valueField", "objectid");
            }});
        }

        @Override
        public void doCache(PipelineJob job, Object value, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            if (value !=  null)
            {
                String dbId = ConvertHelper.convert(value, String.class);
                TableInfo ti = DbSchema.get("blast", DbSchemaType.Module).getTable("databases");
                String containerId = new TableSelector(ti, PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), dbId), null).getObject(String.class);
                Container dbContainer = ContainerManager.getForId(containerId);
                FileContentService fileService = FileContentService.get();
                File fileRoot = fileService == null ? null : fileService.getFileRoot(dbContainer, FileContentService.ContentType.files);
                if (fileRoot == null || !fileRoot.exists())
                {
                    throw new PipelineJobException("Unable to find BLAST DB dir: " + fileRoot);
                }

                File blastDB = new File(fileRoot, ".blastDB");
                blastDB = new File(blastDB, dbId);

                support.cacheObject(getCacheKey(dbId), blastDB);
            }
        }

        public String getCacheKey(String dbId)
        {
            return "blastDB." + dbId;
        }
    }

    @Override
    public PreprocessingOutputImpl processInputFile(File inputFile1, @Nullable File inputFile2, File outputDir) throws PipelineJobException
    {
        PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile1, inputFile2);

        BlastDbDescriptor param = (BlastDbDescriptor)getProvider().getParameterByName(BLAST_DB);
        String blastDbId = extractParamValue(BLAST_DB, String.class);
        File dbDir = getPipelineCtx().getSequenceSupport().getCachedObject(param.getCacheKey(blastDbId), File.class);
        getPipelineCtx().getLogger().debug("using BLAST DB dir: " + dbDir.getPath());

        //convert inputs to single interleaved FASTA
        File fasta;
        if (inputFile2 != null)
        {
            fasta = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".interleaved.fasta");
            new FastqToFastaConverter(getPipelineCtx().getLogger()).createInterleaved(inputFile1, inputFile2, fasta);
        }
        else
        {
            fasta = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".fasta");
            new FastqToFastaConverter(getPipelineCtx().getLogger()).execute(fasta, Arrays.asList(inputFile1, fasta));
        }

        if (!fasta.exists())
        {
            throw new PipelineJobException("unable to find expected file: " + fasta.getPath());
        }
        output.addIntermediateFile(fasta);

        //run BLAST
        File blastOutput = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".blast.out");
        output.addIntermediateFile(blastOutput);

        List<String> blastParams = new ArrayList<>();
        Double eVal = extractParamValue(E_VAL, Double.class);
        blastParams.add("evalue");
        blastParams.add(eVal.toString());

        //always produce ASN.  will convert later
        blastParams.add("-outfmt");
        blastParams.add("6");

        BlastNWrapper blastWrapper = new BlastNWrapper(getPipelineCtx().getLogger());
        blastWrapper.runBlastN(dbDir.getParentFile(), blastDbId, fasta, blastOutput, blastParams);
        output.addCommandsExecuted(blastWrapper.getCommandsExecuted());

        //include/exclude based on output
        Boolean exclude = extractParamValue(EXCLUDE, Boolean.class);
        getPipelineCtx().getLogger().info("reads with BLAST hits will be " + (exclude ? "excluded" : "retained"));

        String extension = FileUtil.getExtension(inputFile1).endsWith("gz") ? ".fastq.gz" : ".fastq";
        File out1 = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".filtered" + extension);
        File out2 = inputFile2 == null ? null : new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile2) + ".filtered" + extension);

        try (CSVReader reader = new CSVReader(Readers.getReader(blastOutput), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {

            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.setProcessedFastq(Pair.of(out1, out2));

        return output;
    }
}
