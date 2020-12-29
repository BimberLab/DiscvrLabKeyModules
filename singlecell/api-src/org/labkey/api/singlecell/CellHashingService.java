package org.labkey.api.singlecell;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.model.CDNA_Library;
import org.labkey.api.singlecell.model.Sample;
import org.labkey.api.singlecell.model.Sort;

import java.io.File;
import java.util.List;
import java.util.Map;

abstract public class CellHashingService
{
    public static final String HASHING_CALLS = "Cell Hashing TCR Calls";

    private static CellHashingService _instance;

    public static CellHashingService get()
    {
        return _instance;
    }

    static public void setInstance(CellHashingService instance)
    {
        _instance = instance;
    }

    abstract public File runCiteSeqCount(PipelineStepOutput output, @Nullable String outputCategory, Readset htoReadset, File htoList, File cellBarcodeList, File outputDir, String basename, Logger log, List<String> extraArgs, boolean doHtoFiltering, @Nullable Integer minCountPerCell, File localPipelineDir, @Nullable Integer editDistance, boolean scanEditDistances, Readset parentReadset, @Nullable Integer genomeId, boolean generateHtoCalls, boolean createOutputFiles, boolean useSeurat, boolean useMultiSeq) throws PipelineJobException;

    abstract public File runRemoteVdjCellHashingTasks(PipelineStepOutput output, String outputCategory, File perCellTsv, Readset rs, SequenceAnalysisJobSupport support, List<String> extraParams, File workingDir, File sourceDir, Integer editDistance, boolean scanEditDistances, Integer genomeId, Integer minCountPerCell, boolean useSeurat, boolean useMultiSeq) throws PipelineJobException;

    abstract public File getCDNAInfoFile(File sourceDir);

    abstract public Map<Integer, Integer> getCachedHashingReadsetMap(Object sequenceJobSupport) throws PipelineJobException;

    abstract public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean skipFailedCdna, boolean failIfNoHashing, boolean failIfNoCiteSeq) throws PipelineJobException;

    abstract public Sample getSampleById(int rowId);

    abstract public CDNA_Library getLibraryById(int rowId);

    abstract public Sort getSortById(int rowId);

    abstract public void processMetrics(SequenceOutputFile so, PipelineJob job, boolean updateDescription) throws PipelineJobException;

    /**
     * Using the sample metadata, return whether cell hashing is used.
     * NOTE: if readset ID is null, this will be interpreted as whether any readset in the input data uses hashing
     */
    abstract public boolean usesCellHashing(SequenceAnalysisJobSupport sequenceAnalysisJobSupport, File sourceDir) throws PipelineJobException;

    abstract public boolean usesCiteSeq(SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException;

    abstract public List<ToolParameterDescriptor> getDefaultHashingParams(boolean includeExcludeFailedcDNA);
}
