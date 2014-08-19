package org.labkey.sequenceanalysis.run.analysis;

import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/7/2014.
 */
public class AnalysisOutputImpl extends DefaultPipelineStepOutput implements AnalysisStep.Output
{
    private List<File> _vcfFiles = new ArrayList<>();

    public AnalysisOutputImpl()
    {

    }

    public void addVcfFile(File file)
    {
        _vcfFiles.add(file);
    }

    @Override
    public List<File> getVcfFiles()
    {
        return Collections.unmodifiableList(_vcfFiles);
    }
}
