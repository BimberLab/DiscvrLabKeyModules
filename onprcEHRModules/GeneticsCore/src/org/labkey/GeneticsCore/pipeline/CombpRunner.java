package org.labkey.GeneticsCore.pipeline;

import org.apache.log4j.Logger;
import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 11/3/2016.
 */
public class CombpRunner extends AbstractCommandWrapper
{
    public CombpRunner(Logger log)
    {
        super(log);
    }

    public File runCompP(File inputBed, File outputDir, int dist, double seed, int stepSize) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        File scriptFile = getScriptFile();
        args.add("bash");
        args.add(scriptFile.getPath());
        args.add(inputBed.getParentFile().getPath());
        args.add(inputBed.getName());
        File outputPrefix = new File(outputDir, FileUtil.getBaseName(inputBed) + ".combp");
        args.add(outputPrefix.getParentFile().getPath());
        args.add(outputPrefix.getName());
        args.add(String.valueOf(dist));
        args.add(String.valueOf(stepSize));
        args.add(String.valueOf(seed));

        execute(args);
        File outputBed = new File(outputPrefix.getPath() + ".regions.bed");
        if (!outputBed.exists())
        {
            throw new PipelineJobException("Unable to find expected output: " + outputBed.getPath());
        }

        return outputBed;
    }

    private File getScriptFile() throws PipelineJobException
    {
        String path = "/external/comb-p/combpWrapper.sh";
        Module module = ModuleLoader.getInstance().getModule(GeneticsCoreModule.NAME);
        Resource script = module.getModuleResource(path);
        if (script == null || !script.exists())
            throw new PipelineJobException("Unable to find file: " + path + " in module: " + GeneticsCoreModule.NAME);

        File f = ((FileResource) script).getFile();
        if (!f.exists())
            throw new PipelineJobException("Unable to find file: " + f.getPath());

        return f;
    }
}
