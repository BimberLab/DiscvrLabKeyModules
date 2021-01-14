package org.labkey.api.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.model.CDNA_Library;
import org.labkey.api.singlecell.model.Sample;
import org.labkey.api.singlecell.model.Sort;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    abstract public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean skipFailedCdna, boolean failIfNoHashing, boolean failIfNoCiteSeq) throws PipelineJobException;

    abstract public File processCellHashingOrCiteSeq(SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters) throws PipelineJobException;

    abstract public File processCellHashingOrCiteSeq(PipelineOutputTracker output, File outputDir, File webserverPipelineDir, Logger log, CellHashingParameters parameters) throws PipelineJobException;

    abstract public File processCellHashingOrCiteSeqForParent(Readset parentReadset, PipelineOutputTracker output, SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters) throws PipelineJobException;

    abstract public File getCDNAInfoFile(File sourceDir);

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

    public static class CellHashingParameters
    {
        public BARCODE_TYPE type;

        public File allBarcodeFile;
        public List<String> allowableBarcodes;

        public File cellBarcodeWhitelistFile;

        public boolean createOutputFiles = true;
        public @Nullable String outputCategory;

        public Readset htoOrCiteseqReadset;
        public Readset parentReadset;

        public @Nullable Integer genomeId;

        public Integer editDistance = 2;
        public boolean scanEditDistances = false;
        public Integer minCountPerCell = 5;
        public List<CALLING_METHOD> methods = CALLING_METHOD.getDefaultMethods();
        public String basename = null;

        private CellHashingParameters()
        {

        }

        public static CellHashingParameters createFromJson(BARCODE_TYPE type, JSONObject params, Readset htoOrCiteseqReadset, @Nullable Readset parentReadset) throws PipelineJobException
        {
            CellHashingParameters ret = new CellHashingParameters();
            ret.type = type;
            ret.scanEditDistances = params.optBoolean("scanEditDistances", false);
            ret.editDistance = params.optInt("editDistance", 2);
            ret.minCountPerCell = params.optInt("minCountPerCell", 3);
            ret.htoOrCiteseqReadset = htoOrCiteseqReadset;
            ret.parentReadset = parentReadset;

            JSONArray methodsArr = params.optJSONArray("methods");
            if (methodsArr != null)
            {
                List<CALLING_METHOD> methods = new ArrayList<>();
                try
                {
                    Arrays.stream(methodsArr.toArray()).forEach(x -> {
                        try
                        {
                            methods.add(CALLING_METHOD.valueOf(String.valueOf(x)));
                        }
                        catch (IllegalArgumentException e)
                        {
                            throw new IllegalArgumentException("Unknown calling method: " + x);
                        }
                    });
                }
                catch (IllegalArgumentException e)
                {
                    throw new PipelineJobException(e.getMessage(), e);
                }
            }

            return ret;
        }

        public int getEffectiveReadsetId()
        {
            return parentReadset != null ? parentReadset.getReadsetId() : htoOrCiteseqReadset.getReadsetId();
        }

        public String getReportTitle()
        {
            return getBasename();
        }

        public String getBasename()
        {
            if (basename != null)
            {
                return FileUtil.makeLegalName(basename);
            }
            else if (parentReadset != null)
            {
                return FileUtil.makeLegalName(parentReadset.getName());
            }
            else if (htoOrCiteseqReadset != null)
            {
                return FileUtil.makeLegalName(htoOrCiteseqReadset.getName());
            }

            throw new IllegalStateException("Neither basename, parent readset, nor hashing/citeseq readset provided");
        }

        public void validate()
        {
            if (htoOrCiteseqReadset == null)
            {
                throw new IllegalStateException("Missing Hashing/Cite-seq readset");
            }

            if (createOutputFiles && outputCategory == null)
            {
                throw new IllegalStateException("Missing output category");
            }

            if (allBarcodeFile == null)
            {
                throw new IllegalStateException("Missing all barcode file");
            }
        }

        public List<String> getAllowableBarcodeNames() throws PipelineJobException
        {
            if (allowableBarcodes != null)
            {
                return Collections.unmodifiableList(allowableBarcodes);
            }
            if (allBarcodeFile == null)
            {
                throw new IllegalArgumentException("Barcode file was null");
            }

            List<String> allowableBarcodes = new ArrayList<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(allBarcodeFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    String val = StringUtils.trimToNull(line[0]);
                    if (val != null)
                    {
                        val = StringUtils.trimToNull(val.split("\t")[0]);
                        if (val != null)
                        {
                            allowableBarcodes.add(val);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return allowableBarcodes;
        }
    }

    public enum CALLING_METHOD
    {
        multiseq(true),
        htodemux(true),
        dropletutils(true),
        threshold(false),
        peaknd(true),
        seqnd(true);

        boolean isDefault;

        CALLING_METHOD(boolean isDefault)
        {
            this.isDefault = isDefault;
        }

        public boolean isDefault()
        {
            return isDefault;
        }

        public static List<CALLING_METHOD> getDefaultMethods()
        {
            return Arrays.stream(values()).filter(CALLING_METHOD::isDefault).collect(Collectors.toList());
        }

        public static List<String> getDefaultMethodNames()
        {
            return getDefaultMethods().stream().map(Enum::name).collect(Collectors.toList());
        }
    }

    public enum BARCODE_TYPE
    {
        hashing(true, true, "Cell Hashing", "cellHashingCalls", null, "MultiSeq Barcodes"),
        citeseq(false, false, "CITE-Seq", "citeSeqCounts", 10, "TotalSeq-C");

        private final boolean _supportsScan;
        private final boolean _doGenerateCalls;
        private final String _label;
        private final String _defaultName;
        private final Integer _defaultTrim;
        private final String _defaultTagGroup;

        BARCODE_TYPE(boolean supportsScan, boolean doGenerateCalls, String label, String defaultName, Integer defaultTrim, String defaultTagGroup) {
            _supportsScan = supportsScan;
            _doGenerateCalls = doGenerateCalls;
            _label = label;
            _defaultName = defaultName;
            _defaultTrim = defaultTrim;
            _defaultTagGroup = defaultTagGroup;
        }

        public boolean isSupportsScan()
        {
            return _supportsScan;
        }

        public boolean doGenerateCalls()
        {
            return _doGenerateCalls;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getDefaultName()
        {
            return _defaultName;
        }

        public Integer getDefaultTrim()
        {
            return _defaultTrim;
        }

        public String getDefaultTagGroup()
        {
            return _defaultTagGroup;
        }
    }
}
