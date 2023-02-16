package org.labkey.api.singlecell.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface AbstractSingleCellStep extends PipelineStep
{
    Collection<String> getRLibraries();

    String getDockerContainerName();

    default void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {

    }

    default boolean createsSeuratObjects()
    {
        return true;
    }

    boolean requiresCiteSeq(SequenceOutputHandler.JobContext ctx);

    boolean requiresHashing(SequenceOutputHandler.JobContext ctx);

    boolean isIncluded(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputs) throws PipelineJobException;

    default String getFileSuffix()
    {
        return getProvider().getName();
    }

    Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException;

    interface Output extends PipelineStepOutput
    {
        /**
         * Returns the cached seurat object
         */
        List<SeuratObjectWrapper> getSeuratObjects();

        File getMarkdownFile();

        File getHtmlFile();
    }

    class SeuratObjectWrapper implements Serializable
    {
        private transient SequenceOutputFile _sequenceOutputFile;

        private Integer _sequenceOutputFileId;
        private Integer _readsetId;

        private File _file;
        private String _datasetId;
        private String _datasetName;

        //For serialization
        public SeuratObjectWrapper()
        {

        }

        public SeuratObjectWrapper(String datasetId, String datasetName, File file, SequenceOutputFile sequenceOutputFile)
        {
            _datasetId = datasetId;
            _datasetName = datasetName;
            _file = file;
            _sequenceOutputFileId = sequenceOutputFile.getRowid();
            _sequenceOutputFile = sequenceOutputFile;
            _readsetId = sequenceOutputFile.getReadset();

        }

        public SeuratObjectWrapper(String datasetId, String datasetName, File file, @Nullable Integer sequenceOutputFileId, @Nullable Integer readsetId)
        {
            _datasetId = datasetId;
            _datasetName = datasetName;
            _file = file;
            _sequenceOutputFileId = sequenceOutputFileId;
            _sequenceOutputFile = null;
            _readsetId = readsetId;
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

        public Integer getReadsetId()
        {
            return _readsetId;
        }

        public void setReadsetId(Integer readsetId)
        {
            _readsetId = readsetId;
        }

        public Integer getSequenceOutputFileId()
        {
            return _sequenceOutputFileId;
        }

        public void setSequenceOutputFileId(Integer sequenceOutputFileId)
        {
            _sequenceOutputFileId = sequenceOutputFileId;
        }

        @JsonIgnore
        public SequenceOutputFile getSequenceOutputFile()
        {
            return _sequenceOutputFile;
        }

        @JsonIgnore
        public void setSequenceOutputFile(SequenceOutputFile sequenceOutputFile)
        {
            _sequenceOutputFile = sequenceOutputFile;
        }
    }
}
