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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.settings.AppProps;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: bbimber
 * Date: 4/23/12
 * Time: 10:23 AM
 */
public class SequencePipelineSettings
{
    private Map<String, String> _params;
    private List<ReadsetModel> _readsets = null;

    //instrument import
    private String _runName;
    private Integer _instrumentId;
    private Date _runDate;

    public SequencePipelineSettings(Map<String, String> params)
    {
        setParams(params);
    }

    public void setParams(Map<String, String> params)
    {
        _params = new HashMap<>(params);

        AppProps.Interface appProps = AppProps.getInstance();
        if (appProps != null)
            _params.put("serverBaseUrl", appProps.getBaseServerUrl());

        initInstrumentRun();
    }

    private void initInstrumentRun()
    {
        _runName = _params.get("runName");
        _instrumentId = getInt(_params.get("instrumentId"));
        _runDate = ConvertHelper.convert(_params.get("runDate"), Date.class);
    }

    private List<ReadsetModel> parseReadsets()
    {
        List<ReadsetModel> readsets = new ArrayList<>();
        for (String key : _params.keySet())
        {
            if (key.startsWith("sample_"))
            {
                readsets.add(createReadsetModel(new JSONObject(_params.get(key))));
            }
        }

        return readsets;
    }

    public ReadsetModel createReadsetModel(JSONObject o)
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
        model.setApplication(o.getString("application"));
        model.setInputMaterial(o.getString("inputmaterial"));
        model.setSampleType(o.getString("sampletype"));
        model.setName(o.getString("readsetname"));
        model.setReadsetId(getInt(o.getString("readset")));
        model.setInstrumentRunId(getInt(o.getString("instrument_run_id")));

        return model;
    }

    private Integer getInt(String v)
    {
        return ConvertHelper.convert(v, Integer.class);
    }

    public String getBasename(String filename)
    {
        String basename;
        if (isDoMerge()){
            basename = _params.get("inputfile.merge.basename");
        }
        else {
            basename = SequenceTaskHelper.getMinimalBaseName(filename);
        }

        return basename;
    }

    public boolean doAutoCreateReadsets()
    {
        return "true".equals(_params.get("autoCreateReadsets"));
    }

    public String getProtocolDescription()
    {
        return _params.get("protocolDescription");
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

    public boolean isDoMerge()
    {
        return ("true".equals(_params.get("inputfile.merge")));
    }

    public String getMergeFilename()
    {
        return _params.get("inputfile.merge.basename") + ".fastq";
    }

    public boolean isDoBarcode()
    {
        return ("true".equals(_params.get("inputfile.barcode")));
    }

    public boolean isRunFastqc()
    {
        return ("true".equals(_params.get("inputfile.runFastqc")));
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
        if (!getBarcodeGroupsToScan().isEmpty())
        {
            return BarcodeModel.getByGroups(getBarcodeGroupsToScan());
        }

        return null;
    }

    public Map<String, String> getParams()
    {
        return _params;
    }

    public boolean isDebugMode()
    {
        return ("true".equals(_params.get("debugMode")));
    }

    public List<ReadsetModel> getReadsets()
    {
        if (_readsets == null)
        {
            _readsets = parseReadsets();
        }

        return _readsets;
    }

    public String getRunName()
    {
        return _runName;
    }

    public Integer getInstrumentId()
    {
        return _instrumentId;
    }

    public Date getRunDate()
    {
        return _runDate;
    }
}
