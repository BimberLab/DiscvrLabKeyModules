/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.sequenceanalysis.model.AdapterModel;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.model.ReadsetModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/23/12
 * Time: 10:23 AM
 */
public class SequencePipelineSettings
{
    public static String AA_SNP_BY_CODON_AGGREGATOR = "aaSnpByCodon";
    public static String NT_SNP_AGGREGATOR = "ntSnpByPosition";
    public static String COVERAGE_AGGREGATOR = "ntCoverage";
    public static String SBT_AGGREGATOR = "sbtAnalysis";
    private Map<String, String> _params;
    private List<ReadsetModel> _readsets = new ArrayList<>();

    //instrument import
    private String _runName;
    private String _instrumentName;
    private Integer _instrumentId;
    private Date _runDate;

    public SequencePipelineSettings(Map<String, String> params)
    {
        setParams(params);
    }

    public void setParams(Map<String, String> params)
    {
        _params = params;

        AppProps.Interface appProps = AppProps.getInstance();
        if(appProps != null)
            _params.put("serverBaseUrl", appProps.getBaseServerUrl());

        initSamples();
        initInstrumentRun();
    }

    private void initInstrumentRun()
    {
        _runName = _params.get("runName");
        _instrumentName = _params.get("instrumentName");
        _instrumentId = getInt(_params.get("instrumentId"));
        _runDate = getDate(_params.get("runDate"));
    }

    private void initSamples()
    {
        for(String key : _params.keySet())
        {
            if(key.startsWith("sample_"))
            {
                _readsets.add(createReadsetModel(new JSONObject(_params.get(key))));
            }
        }
    }

    private ReadsetModel createReadsetModel(JSONObject o)
    {
        ReadsetModel model = new ReadsetModel();
        //_rawJSON = o;
        model.setFileName(o.getString("fileName"));
        model.setFileName2(o.getString("fileName2"));
        //these will be set by the task
        model.setFileId(null); //getInt(o.getString("fileId"));
        model.setFileId2(null); //getInt(o.getString("fileId2"));
        model.setMid5(o.getString("mid5"));
        model.setMid3(o.getString("mid3"));

        //if this is normalization, the inputs used in this pipeline are actually the raw inputs
        //_rawInputFile = getInt(o.getString("fileId"));
        //_rawInputFile2 = getInt(o.getString("fileId2"));
        model.setSampleId(getInt(o.getString("sampleid")));
        model.setSubjectId(o.getString("subjectid"));
        if (o.containsKey("sampledate") && o.get("sampledate") != null && StringUtils.trimToNull(o.getString("sampledate")) != null)
        {
            try
            {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                Date date = format.parse(o.getString("sampledate"));
                model.setSampleDate(date);
            }
            catch (ParseException e)
            {
                throw new IllegalArgumentException("Unable to parse sampleDate: [" +  o.getString("sampledate") + "]");
            }
        }
        model.setPlatform(o.getString("platform"));
        model.setName(o.getString("readsetname"));
        model.setReadsetId(getInt(o.getString("readset")));
        model.setInstrumentRunId(getInt(o.getString("instrument_run_id")));

        return model;
    }

    private Integer getInt(String v)
    {
        return StringUtils.isEmpty(v) ? null : Integer.parseInt(v);
    }

    private Date getDate(String v)
    {
        try
        {
            return StringUtils.isEmpty(v) ? null : new Date(DateUtil.parseDateTime(v));
        }
        catch (ConversionException e)
        {
            return null;
        }
    }

    public String getBasename(String filename)
    {
        String basename;
        if(isDoMerge()){
            basename = _params.get("inputfile.merge.basename");
        }
        else {
            basename = SequenceTaskHelper.getMinimalBaseName(filename);
        }

        return basename;
    }

    public boolean isDoPreprocess()
    {
        return  (!"".equals(_params.get("preprocessing.minLength"))) ||
            ("true".equals(_params.get("preprocessing.downsample"))) ||
            ("true".equals(_params.get("preprocessing.crop"))) ||
            ("true".equals(_params.get("preprocessing.qual2"))) ||
            ("true".equals(_params.get("preprocessing.trimAdapters")))
            ;
    }

    public boolean doDownsample()
    {
        return "true".equals(_params.get("preprocessing.downsample"));
    }

    public boolean hasCustomReference()
    {
        return "true".equals(_params.get("dna.isCustomReference"));
    }

    public String getCustomReferenceSequence()
    {
        return _params.get("dna.refSequence");
    }

    public String getCustomReferenceSequenceName()
    {
        return _params.get("dna.customReferenceName");
    }

    public boolean doAutoCreateReadsets()
    {
        return "true".equals(_params.get("autoCreateReadsets"));
    }

    public boolean doQualityTrimByWindow()
    {
        return "true".equals(_params.get("preprocessing.qual2"));
    }

    public boolean doCrop()
    {
        return ("true".equals(_params.get("preprocessing.crop"))) && getIntegerParam("preprocessing.crop_cropLength") > 0;
    }

    public Integer getCropLength()
    {
        return getIntegerParam("preprocessing.crop_cropLength");
    }

    public boolean isDeleteIntermediateFiles()
    {
        return "true".equals(_params.get("deleteIntermediateFiles"));
    }

    public boolean doHeadCrop()
    {
        return ("true".equals(_params.get("preprocessing.crop"))) && getIntegerParam("preprocessing.crop_headcropLength") > 0;
    }

    public Integer getHeadCropLength()
    {
        return getIntegerParam("preprocessing.crop_headcropLength");
    }

    public Integer getSlidingWindowSize()
    {
        return getIntegerParam("preprocessing.qual2_windowSize");
    }

    public Integer getSlidingWindowQuality()
    {
        return getIntegerParam("preprocessing.qual2_avgQual");
    }

    public String getAnalysisDescription()
    {
        return _params.get("analysisDescription");
    }

    public boolean isDoAdapterTrimming()
    {
        return getAdapters().size() > 0;
    }

    public Integer getSimpleClipThreshold()
    {
        return getIntegerParam("preprocessing.simpleClipThreshold");
    }

    public Integer getPalindromeClipThreshold()
    {
        return getIntegerParam("preprocessing.palindromeClipThreshold");
    }

    public Integer getAdapterSeedMismatches()
    {
        return getIntegerParam("preprocessing.seedMismatches");
    }

    public int getMinReadLength()
    {
        return getIntegerParam("preprocessing.minLength");
    }

    public int getBarcodeMismatches()
    {
        return getIntegerParam("inputfile.barcodeEditDistance");
    }

    public int getBarcodeOffset()
    {
        return getIntegerParam("inputfile.barcodeOffset");
    }

    public int getBarcodeDeletions()
    {
        return getIntegerParam("inputfile.barcodeDeletions");
    }

    public boolean isScanAllBarcodes()
    {
        return "true".equals(_params.get("inputfile.scanAllBarcodes"));
    }

    private int getIntegerParam(String name)
    {
        return StringUtils.isEmpty(_params.get(name)) ? 0 : Integer.parseInt(_params.get(name));
    }

    public List<AdapterModel> getAdapters()
    {
        List<AdapterModel> adapters = new ArrayList<>();
        Map<String, JSONArray> rawData = getAdapterInfo();
        for (JSONArray adapter : rawData.values())
        {
            adapters.add(AdapterModel.fromJSON(adapter));
        }

        return adapters;
    }

    public List<BarcodeModel> getBarcodes()
    {
        List<BarcodeModel> barcodes = new ArrayList<>();
        Map<String, JSONArray> rawData = getBarcodeInfo();
        for (JSONArray bc : rawData.values())
        {
            barcodes.add(BarcodeModel.fromJSON(bc));
        }

        return barcodes;
    }

    private Map<String, JSONArray> getBarcodeInfo()
    {
        Map<String, JSONArray> barcodes = new TreeMap<>();
        for (String key : _params.keySet())
        {
            if (key.startsWith("barcode_"))
            {
                barcodes.put(key, new JSONArray(_params.get(key)));
            }
        }

        return barcodes;
    }

    public SimpleFilter getReferenceFilter()
    {
        SimpleFilter filter = new SimpleFilter();

        for (String key : _params.keySet())
        {
            if (key.startsWith("dna."))
            {
                String val = _params.get(key);

                if ("All".equalsIgnoreCase(val))
                    continue;

                if (StringUtils.trimToNull(val) == null)
                    continue;

                String fieldName = key.replaceAll("dna.", "");
                String[] vals = val.split(",");

                if (vals.length > 1)
                    filter.addClause(new CompareType.CompareClause(FieldKey.fromString(fieldName), CompareType.IN, Arrays.asList(vals)));
                else
                    filter.addClause(new CompareType.CompareClause(FieldKey.fromString(fieldName), CompareType.EQUAL, vals[0]));            }
        }

        return filter;
    }

    private Map<String, JSONArray> getAdapterInfo()
    {
        Map<String, JSONArray> adapters = new TreeMap<>();
        for (String key : _params.keySet())
        {
            if (key.startsWith("adapter_"))
            {
                adapters.put(key, new JSONArray(_params.get(key)));
            }
        }

        return adapters;
    }

    public boolean isDoTrimming()
    {
        return isDoAdapterTrimming() || doCrop() || doHeadCrop() || doQualityTrimByWindow() || getMinReadLength() > 0;
    }

    public boolean isDoAlignment()
    {
        return ("true".equals(_params.get("doAlignment")));
    }

    public String getAligner()
    {
        return _params.get("aligner");
    }

    public Map<String, String> getAlignerOptions()
    {
        String aligner = getAligner();
        Map<String, String> options = new HashMap<>();
        for (String key : _params.keySet())
        {
            if (key.startsWith(aligner))
            {
                String param = key.replaceAll("^" + aligner + ".", "");
                options.put(param, _params.get(key));
            }
        }
        return options;
    }

    public String getRefDbPrefix()
    {
        return _params.get("dbPrefix");
    }

    public String getRefDbFilename()
    {
        return StringUtils.isEmpty(_params.get("dbPrefix")) ? "Ref_DB.fasta" : "Ref_DB." + _params.get("dbPrefix") + ".fasta";
    }

    public boolean hasAdditionalAnalyses()
    {
        return getAggregatorNames().size() > 0;
    }

    public boolean isDoMerge()
    {
        return ("true".equals(_params.get("inputfile.merge")));
    }

    public String getMergeBasename()
    {
        return _params.get("inputfile.merge.basename");
    }

    public String getMergeFilename()
    {
        return _params.get("inputfile.merge.basename") + ".fastq";
    }

    public String getInputfileTreatment()
    {
        return _params.get("inputfile.inputTreatment");
    }

    public boolean isDoBarcode()
    {
        return ("true".equals(_params.get("inputfile.barcode")));
    }

    public boolean isDoPairedEnd()
    {
        return ("true".equals(_params.get("inputfile.pairedend")));
    }

    @NotNull
    public List<String> getBarcodeGroupsToScan()
    {
        String json = _params.get("inputfile.barcodeGroups");
        if (json == null)
            return Collections.emptyList();

        JSONArray array = new JSONArray(json);
        List<String> ret = new ArrayList<>();
        for (Object o : array.toArray())
        {
            ret.add((String)o);
        }
        return ret;
    }

    public BarcodeModel[] getAdditionalBarcodes()
    {
        if (getBarcodeGroupsToScan() != null)
            return BarcodeModel.getByGroups(getBarcodeGroupsToScan());

        return null;
    }

    public Map<String, String> getParams()
    {
        return _params;
    }

    public String getImportType()
    {
        return _params.get("importType");
    }

    public String getAnalysisType()
    {
        return _params.get("analysisType");
    }

    public Integer getDownsampleReadNumber()
    {
        return StringUtils.isEmpty(_params.get("preprocessing.downsampleReadNumber")) ? null : Integer.parseInt(_params.get("preprocessing.downsampleReadNumber"));
    }

    public boolean getSBTonlyImportPairs()
    {
        return StringUtils.isEmpty(_params.get("sbt.onlyImportPairs")) ? false : Boolean.parseBoolean(_params.get("sbt.onlyImportPairs"));
    }

    public Double getSBTminPctToImport()
    {
        return StringUtils.isEmpty(_params.get("sbt.minPctToImport")) ? null : Double.parseDouble(_params.get("sbt.minPctToImport"));
    }

    public boolean isAutomatedImport()
    {
        return ("true".equals(_params.get("automatedImport")));
    }

    public boolean isDebugMode()
    {
        return ("true".equals(_params.get("debugMode")));
    }

    public List<ReadsetModel> getReadsets()
    {
        return _readsets;
    }

    public String getRunName()
    {
        return _runName;
    }

    public String getInstrumentName()
    {
        return _instrumentName;
    }

    public Integer getInstrumentId()
    {
        return _instrumentId;
    }

    public Date getRunDate()
    {
        return _runDate;
    }

    public List<String> getAggregatorNames()
    {
        String[] aggregators = new String[]{NT_SNP_AGGREGATOR, AA_SNP_BY_CODON_AGGREGATOR, COVERAGE_AGGREGATOR, SBT_AGGREGATOR};
        List<String> aggregatorNames = new ArrayList<>();
        for (String param : aggregators)
        {
            if ("true".equals(_params.get(param)))
            {
                aggregatorNames.add(param);
            }
        }
        return aggregatorNames;
    }
}
