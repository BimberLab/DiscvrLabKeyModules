package org.labkey.api.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class CellHashingService
{
    public static final String MAX_HASHING_PCT_FAIL = "maxHashingPctFail";
    public static final String MAX_HASHING_PCT_DISCORDANT = "maxHashingPctDiscordant";

    private static CellHashingService _instance;

    public static CellHashingService get()
    {
        return _instance;
    }

    static public void setInstance(CellHashingService instance)
    {
        _instance = instance;
    }

    abstract public void prepareHashingForVdjIfNeeded(SequenceOutputHandler.JobContext ctx, final boolean failIfNoHashingReadset) throws PipelineJobException;

    abstract public File generateHashingCallsForRawMatrix(Readset parentReadset, PipelineOutputTracker output, SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters, File rawCountMatrixDir) throws PipelineJobException;

    abstract public File getH5FileForGexReadset(SequenceAnalysisJobSupport support, int readsetId, int genomeId) throws PipelineJobException;

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

    abstract public List<ToolParameterDescriptor> getHashingCallingParams(boolean allowMethodsNeedingGex);

    abstract public Set<String> getHtosForParentReadset(Integer parentReadsetId, File webserverJobDir, SequenceAnalysisJobSupport support, boolean throwIfNotFound) throws PipelineJobException;

    abstract public File getExistingFeatureBarcodeCountDir(Readset parentReadset, BARCODE_TYPE type, SequenceAnalysisJobSupport support) throws PipelineJobException;

    abstract public void copyHtmlLocally(SequenceOutputHandler.JobContext ctx) throws PipelineJobException;

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
        public Double majorityConsensusThreshold = null;
        public Double callerDisagreementThreshold = null;
        public List<CALLING_METHOD> methods = CALLING_METHOD.getDefaultConsensusMethods(); //Default to just executing the set used for default consensus calls, rather than additional ones
        public List<CALLING_METHOD> consensusMethods = null;
        public String basename = null;
        public Integer cells = 0;
        public boolean keepMarkdown = false;
        public File h5File = null;
        public boolean doTSNE = true;

        private CellHashingParameters()
        {

        }

        public static CellHashingService.CellHashingParameters createFromStep(SequenceOutputHandler.JobContext ctx, SingleCellStep step, CellHashingService.BARCODE_TYPE type, Readset htoReadset, @Nullable Readset parentReadset) throws PipelineJobException
        {
            CellHashingService.CellHashingParameters ret = new CellHashingService.CellHashingParameters();
            ret.type = type;
            ret.skipNormalizationQc = step.getProvider().getParameterByName("skipNormalizationQc").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Boolean.class, false);
            ret.minCountPerCell = step.getProvider().getParameterByName("minCountPerCell").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Integer.class, 3);
            ret.majorityConsensusThreshold = step.getProvider().getParameterByName("majorityConsensusThreshold").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Double.class, null);
            ret.callerDisagreementThreshold = step.getProvider().getParameterByName("callerDisagreementThreshold").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Double.class, null);
            ret.doTSNE = step.getProvider().getParameterByName("doTSNE").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), Boolean.class, null);
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

                String methodStr2 = StringUtils.trimToNull(step.getProvider().getParameterByName("consensusMethods").extractValue(ctx.getJob(), step.getProvider(), step.getStepIdx(), String.class));
                if (methodStr2 != null)
                {
                    ret.consensusMethods = extractMethods(methodStr2);
                    if (!ret.methods.containsAll(ret.consensusMethods))
                    {
                        throw new PipelineJobException("All consensusMethods must be present in methods: " + methodStr2);
                    }
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
            ret.majorityConsensusThreshold = params.get("majorityConsensusThreshold") == null ? null : params.getDouble("majorityConsensusThreshold");
            ret.callerDisagreementThreshold = params.get("callerDisagreementThreshold") == null ? null : params.getDouble("callerDisagreementThreshold");
            ret.doTSNE = params.get("doTSNE") == null || params.getBoolean("doTSNE");
            ret.retainRawCountFile = params.optBoolean("retainRawCountFile", true);
            ret.htoReadset = htoReadset;
            ret.parentReadset = parentReadset;
            ret.htoBarcodesFile = new File(webserverDir, type.getAllBarcodeFileName());
            ret.methods = extractMethods(params.optString("methods"));
            ret.consensusMethods = extractMethods(params.optString("consensusMethods"));

            if (type == BARCODE_TYPE.hashing && ret.methods.isEmpty())
            {
                throw new IllegalArgumentException("Must provide at least one calling method");
            }

            if (ret.consensusMethods != null && !ret.consensusMethods.isEmpty())
            {
                if (!ret.methods.containsAll(ret.consensusMethods))
                {
                    throw new PipelineJobException("All consensusMethods must be present in methods: " + ret.consensusMethods.stream().map(CALLING_METHOD::name).collect(Collectors.joining(",")));
                }
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
        multiseq(true, false),
        htodemux(false, false),
        dropletutils(true, true),
        gmm_demux(true, true),
        demuxem(true, true, true),
        demuxmix(true, true, true),
        bff_cluster(true, true),
        bff_raw(true, false);

        boolean isDefaultRun;
        boolean isDefaultConsensus;
        boolean requiresH5;

        CALLING_METHOD(boolean isDefaultRun, boolean isDefaultConsensus)
        {
            this(isDefaultRun, isDefaultConsensus, false);
        }

        CALLING_METHOD(boolean isDefaultRun, boolean isDefaultConsensus, boolean requiresH5)
        {
            this.isDefaultRun = isDefaultRun;
            this.isDefaultConsensus = isDefaultConsensus;
            this.requiresH5 = requiresH5;
        }

        public boolean isDefaultRun()
        {
            return isDefaultRun;
        }

        public boolean isDefaultConsensus()
        {
            return isDefaultConsensus;
        }

        public boolean isRequiresH5()
        {
            return requiresH5;
        }

        private static List<CALLING_METHOD> getDefaultRunMethods()
        {
            return Arrays.stream(values()).filter(CALLING_METHOD::isDefaultRun).collect(Collectors.toList());
        }

        private static List<CALLING_METHOD> getDefaultConsensusMethods()
        {
            return Arrays.stream(values()).filter(CALLING_METHOD::isDefaultConsensus).collect(Collectors.toList());
        }

        public static List<String> getDefaultRunMethodNames()
        {
            return getDefaultRunMethods().stream().map(Enum::name).collect(Collectors.toList());
        }

        public static List<String> getDefaultConsensusMethodNames()
        {
            return getDefaultConsensusMethods().stream().map(Enum::name).collect(Collectors.toList());
        }

        public static boolean requiresH5(String methodNames)
        {
            methodNames = StringUtils.trimToNull(methodNames);
            if (methodNames == null)
            {
                return false;
            }

            return requiresH5(Arrays.stream(methodNames.split(";")).map(CALLING_METHOD::valueOf).collect(Collectors.toList()));
        }

        public static boolean requiresH5(Collection<CALLING_METHOD> methods)
        {
            return methods.stream().anyMatch(CALLING_METHOD::isRequiresH5);
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
