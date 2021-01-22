package org.labkey.api.singlecell.pipeline;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.util.Collection;
import java.util.List;

public interface SingleCellStep extends PipelineStep
{
    public static final String STEP_TYPE = "singleCell";
    public static final String SEURAT_PROCESSING = "seuratProcessing";

    public Collection<String> getRLibraries();

    public String getDockerContainerName();

    default void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {

    }

    default boolean createsSeuratObjects()
    {
        return true;
    }

    public boolean requiresHashingOrCiteSeq();

    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException;

    public static interface Output extends PipelineStepOutput
    {
        /**
         * Returns the cached seurat object
         */
        public List<SeuratObjectWrapper> getSeuratObjects();

        public File getMarkdownFile();

        public File getHtmlFile();
    }

    public static class SeuratObjectWrapper
    {
        private File _file;
        private String _datasetId;
        private String _datasetName;

        public SeuratObjectWrapper(String datasetId, String datasetName, File file)
        {
            _datasetId = datasetId;
            _datasetName = datasetName;
            _file = file;
        }

        public File getFile()
        {
            return _file;
        }

        public void setFile(File file)
        {
            _file = file;
        }

        public String getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(String datasetId)
        {
            _datasetId = datasetId;
        }

        public String getDatasetName()
        {
            return _datasetName;
        }

        public void setDatasetName(String datasetName)
        {
            _datasetName = datasetName;
        }
    }
}
