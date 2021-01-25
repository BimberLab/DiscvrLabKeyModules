package org.labkey.api.singlecell.pipeline;

import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SingleCellOutput extends DefaultPipelineStepOutput implements SingleCellStep.Output
{
    private List<SingleCellStep.SeuratObjectWrapper> _seuratObjects = new ArrayList<>();
    private File _markdownFile;
    private File _htmlFile;

    @Override
    public List<SingleCellStep.SeuratObjectWrapper> getSeuratObjects()
    {
        return _seuratObjects;
    }

    @Override
    public File getMarkdownFile()
    {
        return _markdownFile;
    }

    @Override
    public File getHtmlFile()
    {
        return _htmlFile;
    }

    public void setSeuratObjects(List<SingleCellStep.SeuratObjectWrapper> seuratObjects)
    {
        _seuratObjects = seuratObjects;
    }

    public void setMarkdownFile(File markdownFile)
    {
        _markdownFile = markdownFile;
    }

    public void setHtmlFile(File htmlFile)
    {
        _htmlFile = htmlFile;
    }
}
