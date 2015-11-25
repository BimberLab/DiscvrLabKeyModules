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

import com.drew.lang.annotations.Nullable;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.sequenceanalysis.FileGroup;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.BarcodeModel;

import java.io.File;
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
    private List<SequenceReadsetImpl> _readsets = null;
    private List<FileGroup> _fileGroups = null;

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
        _runDate = StringUtils.trimToNull(_params.get("runDate")) == null ? null : ConvertHelper.convert(_params.get("runDate"), Date.class);
    }

    private void parseReadsets(@Nullable SequenceAnalysisJob job) throws PipelineJobException
    {
        _readsets = new ArrayList<>();
        _fileGroups = new ArrayList<>();

        for (String key : _params.keySet())
        {
            if (key.startsWith("fileGroup_"))
            {
                _fileGroups.add(createFileGroup(new JSONObject(_params.get(key)), job));
            }
        }

        for (String key : _params.keySet())
        {
            if (key.startsWith("readset_"))
            {
                JSONObject json = new JSONObject(_params.get(key));
                SequenceReadsetImpl rs = createReadsetModel(json);
                _readsets.add(rs);
            }
        }
    }

    private FileGroup createFileGroup(JSONObject o, @Nullable SequenceAnalysisJob job) throws PipelineJobException
    {
        if (!o.containsKey("files"))
        {
            throw new PipelineJobException("Malformed file group JSON");
        }

        JSONArray files = o.getJSONArray("files");
        FileGroup fg = new FileGroup();
        fg.name = o.getString("name");

        for (JSONObject json : files.toJSONObjectArray())
        {
            FileGroup.FilePair p = new FileGroup.FilePair();
            p.platformUnit = StringUtils.trimToNull(json.getString("platformUnit"));
            p.centerName = StringUtils.trimToNull(json.getString("centerName"));

            if (json.containsKey("file1"))
            {
                JSONObject fileJson = json.getJSONObject("file1");
                File f = resolveFile(fileJson, job);
                p.file1 = f;
            }

            if (json.containsKey("file2"))
            {
                File f = resolveFile(json.getJSONObject("file2"), job);
                p.file2 = f;
            }

            fg.filePairs.add(p);
        }

        return fg;
    }

    public SequenceReadsetImpl createReadsetModel(JSONObject o)
    {
        SequenceReadsetImpl model = new SequenceReadsetImpl();

        model.setBarcode5(StringUtils.trimToNull(o.getString("barcode5")));
        model.setBarcode3(StringUtils.trimToNull(o.getString("barcode3")));
        model.setSampleId(getInt(o.getString("sampleid")));
        model.setSubjectId(o.getString("subjectid"));
        model.setComments(o.getString("comments"));
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
        model.setSampleType(o.getString("sampletype"));
        model.setLibraryType(o.getString("librarytype"));
        model.setName(o.getString("readsetname"));
        if (StringUtils.trimToNull(o.getString("readset")) != null)
            model.setRowId(getInt(o.getString("readset")));

        if (StringUtils.trimToNull(o.getString("instrument_run_id")) != null)
            model.setInstrumentRunId(o.getInt("instrument_run_id"));

        if (o.containsKey("fileGroupId"))
        {
            model.setFileSetName(o.getString("fileGroupId"));
        }

        return model;
    }

    private File resolveFile(JSONObject json, @Nullable SequenceAnalysisJob job)
    {
        if (json.containsKey("dataId"))
        {
            Integer dataId = ConvertHelper.convert(json.get("dataId"), Integer.class);
            if (dataId != null)
            {
                if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
                {
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (d == null)
                    {
                        throw new IllegalArgumentException("Unable to find ExpData: " + json.get("dataId"));
                    }

                    return d.getFile();
                }
                else
                {
                    if (job != null && job.getCachedData(dataId) != null)
                    {
                        job.getLogger().debug("found using cached ExpData");
                        return job.getCachedData(dataId);
                    }
                }
            }
        }

        if (json.containsKey("fileName"))
        {
            //resolve based on inputs
            if (job != null)
            {
                for (File input : job.getJobSupport(FileAnalysisJobSupport.class).getInputFiles())
                {
                    if (input.getName().equals(json.getString("fileName")))
                    {
                        if (input.exists())
                        {
                            return input;
                        }
                        else
                        {
                            //file might have been a copied input, check in analysis directory
                            File test = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), input.getName());
                            if (test.exists())
                            {
                                return test;
                            }
                        }
                    }
                }
            }
        }

        if (job != null)
        {
            job.getLogger().error("unable to find file: " + json.toString());
            job.getLogger().debug("input files were: ");
            for (File f : job.getJobSupport(FileAnalysisJobSupport.class).getInputFiles())
            {
                job.getLogger().debug("[" + f.getPath() + "], exists: " + f.exists());
            }
        }

        return null;
    }

    private Integer getInt(String v)
    {
        return ConvertHelper.convert(v, Integer.class);
    }

    public boolean doAutoCreateReadsets()
    {
        return "true".equals(_params.get("autoCreateReadsets"));
    }

    public boolean doCollectWgsMetrics()
    {
        return "true".equals(_params.get("collectWgsMetrics"));
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

    public boolean isDoBarcode()
    {
        return ("true".equals(_params.get("inputfile.barcode")));
    }

    public boolean isRunFastqc()
    {
        return ("true".equals(_params.get("inputfile.runFastqc")));
    }

    public Map<String, String> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public boolean isDebugMode()
    {
        return ("true".equals(_params.get("debugMode")));
    }

    public List<SequenceReadsetImpl> getReadsets(@Nullable SequenceAnalysisJob job)
    {
        if (_readsets == null)
        {
            try
            {
                parseReadsets(job);
            }
            catch (PipelineJobException e)
            {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        return _readsets;
    }

    public List<FileGroup> getFileGroups(SequenceAnalysisJob job) throws PipelineJobException
    {
        if (_fileGroups == null)
        {
            parseReadsets(job);
        }

        return _fileGroups;
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
