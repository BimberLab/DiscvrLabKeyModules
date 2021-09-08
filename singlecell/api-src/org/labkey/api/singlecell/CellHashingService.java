package org.labkey.api.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class CellHashingService
{
    private static CellHashingService _instance;

    public static CellHashingService get()
    {
        return _instance;
    }

    static public void setInstance(CellHashingService instance)
    {
        _instance = instance;
    }

    abstract public void prepareHashingIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean failIfNoHashing) throws PipelineJobException;

    abstract public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, boolean failIfNoHashing, boolean failIfNoCiteSeq) throws PipelineJobException;

    abstract public File generateHashingCallsForRawMatrix(Readset parentReadset, PipelineOutputTracker output, SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters, File rawCountMatrixDir) throws PipelineJobException;

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

    abstract public List<ToolParameterDescriptor> getHashingCallingParams();

    abstract public Set<String> getHtosForParentReadset(Integer parentReadsetId, File webserverJobDir, SequenceAnalysisJobSupport support) throws PipelineJobException;

    abstract public File getExistingFeatureBarcodeCountDir(Readset parentReadset, BARCODE_TYPE type, SequenceAnalysisJobSupport support) throws PipelineJobException;

    public static class CellHashingParameters
    {
        public BARCODE_TYPE type;

        private File htoBarcodesFile;
        public Set<String> allowableHtoBarcodes;

        public File cellBarcodeWhitelistFile;

        public boolean createOutputFiles = true;
        public @Nullable String outputCategory;
        public boolean retainRawCountFile = false;

        public Readset htoReadset;
        public Readset parentReadset;

        public @Nullable Integer genomeId;
        public boolean skipNormalizationQc = false;
        public Integer minCountPerCell = 5;
        public List<CALLING_METHOD> methods = CALLING_METHOD.getDefaultMethods();
        public String basename = null;
        public Integer cells = 0;
        public boolean keepMarkdown = false;

        private CellHashingParameters()
        {

        }

        public static CellHashingService.CellHashingParameters createFromStep(SequenceOutputHandler.JobContext ctx, SingleCellStep step, CellHashingService.BARCODE_TYPE type, Readset htoReadset, @Nullable Readset parentReadset) throws PipelineJobException
        {
            CellHashingService.CellHashingParameters ret = new CellHashingService.CellHashingParameters();
            ret.type = type;
            ret.skipNormalizationQc = step.getProvider().getParameterByName("skipNormalizationQc").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Boolean.class, false);
            ret.minCountPerCell = step.getProvider().getParameterByName("minCountPerCell").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Integer.class, 3);
            ret.retainRawCountFile = step.getProvider().getParameterByName("retainRawCountFile").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Boolean.class, true);
            ret.htoReadset = htoReadset;
            ret.parentReadset = parentReadset;
            ret.htoBarcodesFile = new File(ctx.getSourceDirectory(), type.getAllBarcodeFileName());

            if (type == BARCODE_TYPE.hashing)
            {
                String methodStr = StringUtils.trimToNull(step.getProvider().getParameterByName("methods").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), String.class));
                if (methodStr != null)
                {
                    ret.methods = extractMethods(methodStr);
                }

                if (ret.methods.isEmpty())
                {
                    throw new IllegalArgumentException("Must provide at least one calling method");
                }
            }

            return ret;
        }

        public static CellHashingParameters createFromJson(BARCODE_TYPE type, File webserverDir, JSONObject params, Readset htoReadset, @Nullable Readset parentReadset) throws PipelineJobException
        {
            CellHashingParameters ret = new CellHashingParameters();
            ret.type = type;
            ret.skipNormalizationQc = params.optBoolean("skipNormalizationQc", false);
            ret.minCountPerCell = params.optInt("minCountPerCell", 3);
            ret.retainRawCountFile = params.optBoolean("retainRawCountFile", true);
            ret.htoReadset = htoReadset;
            ret.parentReadset = parentReadset;
            ret.htoBarcodesFile = new File(webserverDir, type.getAllBarcodeFileName());
            ret.methods = extractMethods(params.optString("methods"));

            if (type == BARCODE_TYPE.hashing && ret.methods.isEmpty())
            {
                throw new IllegalArgumentException("Must provide at least one calling method");
            }

            return ret;
        }

        private static @NotNull List<CALLING_METHOD> extractMethods(String methodsStr) throws PipelineJobException
        {
            if (methodsStr != null)
            {
                String[] tokens = methodsStr.split(";");

                List<CALLING_METHOD> methods = new ArrayList<>();
                try
                {
                    Arrays.stream(tokens).forEach(x -> {
                        try
                        {
                            methods.add(CALLING_METHOD.valueOf(String.valueOf(x)));
                        }
                        catch (IllegalArgumentException e)
                        {
                            throw new IllegalArgumentException("Unknown calling method: " + x);
                        }
                    });

                    return methods;
                }
                catch (IllegalArgumentException e)
                {
                    throw new PipelineJobException(e.getMessage(), e);
                }
            }

            return Collections.emptyList();
        }

        public int getEffectiveReadsetId()
        {
            return parentReadset != null ? parentReadset.getReadsetId() : htoReadset.getReadsetId();
        }

        public File getHtoBarcodeFile()
        {
            return htoBarcodesFile;
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
            else if (htoReadset != null)
            {
                return FileUtil.makeLegalName(htoReadset.getName());
            }

            throw new IllegalStateException("Neither basename, parent readset, nor hashing/citeseq readset provided");
        }

        public void validate()
        {
            validate(false);
        }

        public void validate(boolean allowMissingHtoReadset)
        {
            if (!allowMissingHtoReadset && htoReadset == null)
            {
                throw new IllegalStateException("Missing Hashing readset");
            }

            if (createOutputFiles && outputCategory == null)
            {
                throw new IllegalStateException("Missing output category");
            }

            if (htoBarcodesFile == null)
            {
                throw new IllegalStateException("Missing all HTO barcodes file");
            }
        }

        public Set<String> getAllowableBarcodeNames() throws PipelineJobException
        {
            if (allowableHtoBarcodes != null)
            {
                return Collections.unmodifiableSet(allowableHtoBarcodes);
            }
            if (htoBarcodesFile == null)
            {
                throw new IllegalArgumentException("Barcode file was null");
            }

            Set<String> allowableBarcodes = new HashSet<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(htoBarcodesFile), '\t'))
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
        multiseq(false),
        htodemux(false),
        dropletutils(true),
        gmm_demux(true),
        bff_cluster(true),
        bff_raw(false);

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
        hashing(true, true, "Cell Hashing", "cellHashingCalls", null, "MultiSeq Barcodes", "allHTOBarcodes.txt"),
        citeseq(false, false, "CITE-Seq", "citeSeqCounts", 10, "TotalSeq-C", "allCiteSeqBarcodes.txt");

        private final boolean _supportsScan;
        private final boolean _doGenerateCalls;
        private final String _label;
        private final String _defaultName;
        private final Integer _defaultTrim;
        private final String _defaultTagGroup;
        private final String _allBarcodeFileName;

        BARCODE_TYPE(boolean supportsScan, boolean doGenerateCalls, String label, String defaultName, Integer defaultTrim, String defaultTagGroup, String allBarcodeFileName) {
            _supportsScan = supportsScan;
            _doGenerateCalls = doGenerateCalls;
            _label = label;
            _defaultName = defaultName;
            _defaultTrim = defaultTrim;
            _defaultTagGroup = defaultTagGroup;
            _allBarcodeFileName = allBarcodeFileName;
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

        public String getAllBarcodeFileName()
        {
            return _allBarcodeFileName;
        }
    }
}
