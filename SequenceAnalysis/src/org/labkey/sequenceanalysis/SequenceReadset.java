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
package org.labkey.sequenceanalysis;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.query.FieldKey;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 5/25/12
 * Time: 2:44 PM
 */
public class SequenceReadset
{
    private int _rowId;
    private Integer _fileId;
    private Integer _fileId2;
    private String _name;
    private String _comments;
    private String _platform;
    private Integer _sampleId;
    private String _barcode5;
    private String _barcode3;
    private String _subjectId;
    private Integer _instrument_run_id;
    private Integer _runid;
    private Integer _jobid;
    private Container _container;

    public SequenceReadset()
    {

    }

    public static SequenceReadset getFromId(Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        //NOTE: workbooks / parent prove a problem, so we omit container for now
        //filter.addCondition("Container", c);
        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_READSETS), filter, null);
        if(ts.getRowCount() == 0)
            return null;

        SequenceReadset s = ts.getObject(rowId, SequenceReadset.class);
        return s;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getFileId()
    {
        return _fileId;
    }

    public void setFileId(Integer fileId)
    {
        _fileId = fileId;
    }

    public Integer getFileId2()
    {
        return _fileId2;
    }

    public void setFileId2(Integer fileId2)
    {
        _fileId2 = fileId2;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getPlatform()
    {
        return _platform;
    }

    public void setPlatform(String platform)
    {
        _platform = platform;
    }

    public Integer getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }

    public String getBarcode5()
    {
        return _barcode5;
    }

    public void setBarcode5(String barcode5)
    {
        _barcode5 = barcode5;
    }

    public String getBarcode3()
    {
        return _barcode3;
    }

    public void setBarcode3(String barcode3)
    {
        _barcode3 = barcode3;
    }

    public String getSubjectId()
    {
        return _subjectId;
    }

    public void setSubjectId(String subjectId)
    {
        _subjectId = subjectId;
    }

    public Integer getInstrument_run_id()
    {
        return _instrument_run_id;
    }

    public void setInstrument_run_id(Integer instrument_run_id)
    {
        _instrument_run_id = instrument_run_id;
    }

    public Integer getRunid()
    {
        return _runid;
    }

    public void setRunid(Integer runid)
    {
        _runid = runid;
    }

    public Integer getJobid()
    {
        return _jobid;
    }

    public void setJobid(Integer jobid)
    {
        _jobid = jobid;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(String containerId)
    {
        _container = ContainerManager.getForId(containerId);
    }

    /**
     * Can be used to return a list of the input sequence files for this readset
     * @param user The current user.  Necessary to test security on the readset files, which may reside in different containers
     * @return List<File> A list of the readset files.
     */
    public List<File> getExpDatasForReadset(User user)
    {
        if (getFileId() == null)
            return Collections.emptyList();

        List<File> files = new ArrayList<>();
        ExpData data = ExperimentService.get().getExpData(getFileId());
        if (data != null && data.getFile() != null && data.getContainer().hasPermission(user, ReadPermission.class))
        {
            if(data.getFile().exists())
                files.add(data.getFile());
        }

        if(getFileId2() != null)
        {
            data = ExperimentService.get().getExpData(getFileId2());
            if (data != null && data.getFile() != null && data.getContainer().hasPermission(user, ReadPermission.class))
            {
                if(data.getFile().exists())
                    files.add(data.getFile());
            }
        }

        return files;
    }
}
