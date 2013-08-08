package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/14/12
 * Time: 7:40 AM
 */
public class MosaikRunner extends AbstractCommandWrapper
{
    public MosaikRunner(Logger logger)
    {
        super(logger);
    }

    @Override
    protected String getArgPrefix()
    {
        return "-";
    }

    protected File getBuildExe()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("MOSAIKPATH");
        return new File(path, "MosaikBuild");
    }

    protected File getAlignExe()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("MOSAIKPATH");
        return new File(path, "MosaikAlign");
    }

    protected Map<String, CommandArgument> getBuildCommandMap()
    {
        Map<String, CommandArgument> args = new HashMap<>();

        args.put("technology", new CommandArgument("st"));
        args.put("quiet", new CommandSwitch("quiet", true));
        args.put("technology", new CommandArgument("st")); //454, helicos, illumina, sanger, solid
        args.put("assignQual", new CommandArgument("assignQual"));
        args.put("prefix", new CommandArgument("p"));

        return args;
    }

    protected Map<String, CommandArgument> getAlignerCommandMap()
    {
        Map<String, CommandArgument> args = new HashMap<>();

//            'ma|mode' => 'm',
//            'ma|hash_size' => 'hs',
//            'ma|processors' => 'p',
//            'ma|align_threshold' => 'act',
//            'ma|max_mismatch' => 'mm',
//            'ma|max_mismatch_pct' => 'mmp',
//            'ma|min_pct_aligned' => 'minp',
//            'ma|use_aligned_length' => 'mmal',
//            'ma|max_hash_positions' => 'mhp',
//            'ma|jump_db' => 'j',
//            'ma|local_search' => 'ls',
//            'ma|banded' => 'bw',
//            'ma|pe_neural_network' => 'annpe',
//            'ma|se_neural_network' => 'annse',
//            'ma|output_multiple' => 'om',
//
//            'ma|quiet' => 'quiet',

        return args;
    }

    public File buildReference(File input) throws PipelineJobException
    {
        File output = new File(input.getParentFile(), FileUtil.getBaseName(input) + ".mosaik");
        executeBuild(input, null, output, "oa");
        if (!output.exists())
            throw new PipelineJobException("Unable to find file: " + output.getPath());

        return output;
    }

    public File buildReads(File input, @Nullable File input2) throws PipelineJobException
    {
        File output = new File(input.getParentFile(), FileUtil.getBaseName(input) + ".mosaik");
        executeBuild(input, input2, output, "out");
        if (!output.exists())
            throw new PipelineJobException("Unable to find file: " + output.getPath());

        return output;
    }

    private void executeBuild(File input, @Nullable File input2, File output, String outParam) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getBuildExe().getPath());
        args.addAll(getArgs(new HashMap<String, String>(), getBuildCommandMap()));

        SequenceUtil.FILETYPE type = SequenceUtil.inferType(input);
        String paramName1 = null;
        String paramName2 = null;
        if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            paramName1 = "fr";
            paramName2 = "f2";
        }
        else if (type.equals(SequenceUtil.FILETYPE.fastq))
        {
            paramName1 = "q";
            paramName2 = "q2";
        }
        else
        {
            throw new IllegalArgumentException("Unknown input type: " + type.name());
        }

        CommandArgument arg1 = new CommandArgument(paramName1);
        args.addAll(arg1.getArgs(input.getPath()));

        if (input2 != null)
        {
            CommandArgument arg2 = new CommandArgument(paramName2);
            args.addAll(arg2.getArgs(input2.getPath()));
        }

        CommandArgument outArg = new CommandArgument(outParam);
        args.addAll(outArg.getArgs(output.getPath()));

        doExecute(getWorkingDir(input), args);
    }
}
