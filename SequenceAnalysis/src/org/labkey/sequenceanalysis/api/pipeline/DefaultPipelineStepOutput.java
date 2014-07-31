package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 7:54 AM
 */
public class DefaultPipelineStepOutput implements PipelineStepOutput
{
    private List<Pair<File, String>> _outputs = new ArrayList<>();
    private List<File> _intermediateFiles = new ArrayList<>();
    private List<File> _deferredDeleteFiles = new ArrayList<>();

    public DefaultPipelineStepOutput()
    {

    }

    @Override
    public List<Pair<File, String>> getOutputs()
    {
        return Collections.unmodifiableList(_outputs);
    }

    @Override
    public List<File> getOutputsOfRole(String role)
    {
        List<File> ret = new ArrayList<>();
        for (Pair<File, String> pair : _outputs)
        {
            if (role.equals(pair.second))
            {
                ret.add(pair.first);
            }
        }

        return ret;
    }

    @Override
    public List<File> getIntermediateFiles()
    {
        return Collections.unmodifiableList(_intermediateFiles);
    }

    @Override
    public List<File> getDeferredDeleteIntermediateFiles()
    {
        return Collections.unmodifiableList(_deferredDeleteFiles);
    }

    public void addOutput(File output, String role)
    {
        _outputs.add(Pair.of(output, role));
    }

    public void addIntermediateFile(File file)
    {
        addIntermediateFile(file, null);
    }

    public void addIntermediateFile(File file, String role)
    {
        if (role != null)
            addOutput(file, role);

        _intermediateFiles.add(file);
    }

    public void addDeferredDeleteIntermediateFile(File file)
    {
        _deferredDeleteFiles.add(file);
    }
}
