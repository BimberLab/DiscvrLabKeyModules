package org.labkey.GeneticsCore.analysis;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import static org.labkey.GeneticsCore.analysis.MethylationRateComparisonHandler.METHYLATION_TYPE;

/**
 * Created by bimber on 3/22/2017.
 */
public class CombineMethylationRatesHandler extends AbstractParameterizedOutputHandler
{
    public CombineMethylationRatesHandler()
    {
        super(ModuleLoader.getInstance().getModule(GeneticsCoreModule.NAME), "Combine Methylation Rates", "This will take a set of site methylation rates (calculated separately through this module), and combine these into a single table", new LinkedHashSet<>(PageFlowUtil.set("/sequenceanalysis/field/IntervalField.js")), Arrays.asList(
                ToolParameterDescriptor.create("outPrefix", "Output Prefix", "This will be used as the prefix for output files", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("outputDescription", "Description", "This will be used as the description for output files", "textarea", new JSONObject(){{
                    put("allowBlank", false);
                    put("height", 150);
                }}, null),
                ToolParameterDescriptor.create("intervals", "Intervals", "The intervals over which to merge the data.  They should be in the form: chr01:102-20394", "sequenceanalysis-intervalfield", null, null)
        ));
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (METHYLATION_TYPE.isType(f.getFile()));
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
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            String outPrefix = StringUtils.trimToNull(ctx.getParams().optString("outPrefix"));
            String outputDescription = StringUtils.trimToNull(ctx.getParams().optString("outputDescription"));
            String intervalString = StringUtils.trimToNull(ctx.getParams().optString("intervals"));
            if (intervalString == null)
            {
                throw new PipelineJobException("No Intervals Provided");
            }

            List<Pair<Interval, String>> intervals = new ArrayList<>();
            for (String i : intervalString.split(";"))
            {
                String[] tokens = i.split(":|-");
                if (tokens.length != 3)
                {
                    throw new PipelineJobException("Invalid interval: " + i);
                }

                intervals.add(Pair.of(new Interval(tokens[0], Integer.parseInt(StringUtils.trimToNull(tokens[1])), Integer.parseInt(StringUtils.trimToNull(tokens[2]))), ""));
            }

            intervals.sort(Comparator.comparing(o -> o.first));

            File outputTable = new File(ctx.getOutputDir(), FileUtil.makeLegalName(outPrefix) + ".txt");
            MethylationRateComparisonHandler.buildCombinedTable(outputTable, intervals, inputFiles, ctx, false);

            SequenceOutputFile soTable = new SequenceOutputFile();
            soTable.setName(outPrefix);
            soTable.setDescription(outputDescription);
            soTable.setFile(outputTable);
            soTable.setCategory("Combined Methylation Rates");
            ctx.addSequenceOutput(soTable);
        }
    }
}
