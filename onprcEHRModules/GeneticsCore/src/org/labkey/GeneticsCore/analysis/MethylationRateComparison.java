package org.labkey.GeneticsCore.analysis;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.run.RCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 3/18/2015.
 */
public class MethylationRateComparison implements SequenceOutputHandler
{
    public MethylationRateComparison()
    {

    }

    @Override
    public String getName()
    {
        return "Compare Methylation Rates";
    }

    @Override
    public String getDescription()
    {
        return "This can be used to compare site methylation rate data across multiple samples";
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/GeneticsCore/methylationComparison.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(GeneticsCoreModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    private static final FileType _methylationType = new FileType(".methylation.txt");

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _methylationType.isType(f.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            actions.add(action);

            if (params == null)
            {
                throw new PipelineJobException("No parameters provided");
            }

            if (params.containsKey("stds"))
            {
                String ampliconBorders = params.getString("ampliconBorders");
                if (StringUtils.isEmpty(ampliconBorders))
                {
                    throw new PipelineJobException("No amplicon borders provided");
                }

                File ampliconFile = new File(outputDir, "amplicons.txt");
                action.addInputIfNotPresent(ampliconFile, "Amplicons");

                Set<String> ampliconNames = new HashSet<>();
                try (PrintWriter writer = PrintWriters.getPrintWriter(ampliconFile))
                {
                    String[] amplicons = ampliconBorders.split("\\r?\\n");
                    for (String a : amplicons)
                    {
                        writer.write(a);

                        String[] tokens = a.split("\\s+");
                        if (tokens.length < 4)
                        {
                            throw new PipelineJobException("Inproper line in amplicons: [" + a + "]");
                        }

                        ampliconNames.add(tokens[3]);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                Map<String, SequenceOutputFile> fileMap = new HashMap<>();
                for (SequenceOutputFile f : inputFiles)
                {
                    action.addInputIfNotPresent(f.getFile(), "Methlylation Rates");
                    fileMap.put(f.getRowid().toString(), f);
                }

                JSONObject stds = params.getJSONObject("stds");
                File stdsFile = new File(outputDir, "files.txt");
                action.addInputIfNotPresent(stdsFile, "Input Files");
                try (PrintWriter writer = PrintWriters.getPrintWriter(stdsFile))
                {
                    for (String rowId : stds.keySet())
                    {
                        SequenceOutputFile f = fileMap.get(rowId);
                        writer.write(stds.getDouble(rowId) + "\t" + f.getFile().getPath() + "\n");
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                String rScript = getScriptPath();

                List<String> args = new ArrayList<>();
                args.add(rScript);
                args.add("files=" + stdsFile.getPath());
                args.add("amps=" + ampliconFile.getPath());

                RCommandWrapper wrapper = new RCommandWrapper(job.getLogger());
                wrapper.setWorkingDir(outputDir);
                wrapper.executeScript(args);

                for (String ampliconName : ampliconNames)
                {
                    File graph = new File(outputDir, FileUtil.getBaseName(ampliconName) + ".png");
                    if (!graph.exists())
                    {
                        job.getLogger().info("unable to find output, expected: " + graph.getPath());
                    }
                    else
                    {
                        action.addOutput(graph, "Standard Curve", false, true);
                    }

                    File data = new File(outputDir, FileUtil.getBaseName(ampliconName) + ".std_crv_data.txt");
                    if (!data.exists())
                    {
                        job.getLogger().warn("unable to find output, expected: " + data.getPath());
                    }
                    else
                    {
                        action.addOutput(data, "Standard Curve", false, true);
                    }
                }
            }
        }

        private String getScriptPath() throws PipelineJobException
        {
            Module module = ModuleLoader.getInstance().getModule(GeneticsCoreModule.class);
            Resource script = module.getModuleResource("/external/amplicon_standard_curve.R");
            if (!script.exists())
                throw new PipelineJobException("Unable to find file: " + script.getPath());

            File f = ((FileResource) script).getFile();
            if (!f.exists())
                throw new PipelineJobException("Unable to find file: " + f.getPath());

            return f.getPath();
        }
    }
}
