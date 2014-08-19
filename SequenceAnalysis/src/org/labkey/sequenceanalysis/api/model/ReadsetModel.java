package org.labkey.sequenceanalysis.api.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.util.Date;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 11:08 PM
 */
public class ReadsetModel
{
    private Integer _rowId;
    private Integer _fileId;
    private Integer _fileId2;
    private String _fileName;
    private String _fileName2;
    private String _mid5;
    private String _mid3;
    private Integer _runId;

    private Integer _sampleId;
    private String _subjectId;
    private Date _sampleDate;
    private String _platform;
    private String _application;
    private String _inputMaterial;
    private String _name;
    private Integer _instrumentRunId;
    private String _container;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;

    public ReadsetModel()
    {

    }

    public String getExpectedFileNameForPrefix(String prefix)
    {
        return getExpectedFileNameForPrefix(prefix, false);
    }

    public String getExpectedFileNameForPrefix(String prefix, boolean gzip)
    {
        if(StringUtils.isEmpty(prefix))
            return null;

        String name = SequenceTaskHelper.getMinimalBaseName(prefix);
        if(!StringUtils.isEmpty(getMid5()))
            name += "_" + getMid5();
        if(!StringUtils.isEmpty(getMid3()))
            name += "_" + getMid3();

        name += ".fastq";
        if(gzip)
            name += ".gz";

        return name;
    }

    public Integer getFileId()
    {
        return _fileId;
    }

    public Integer getFileId2()
    {
        return _fileId2;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public String getFileName2()
    {
        return _fileName2;
    }

    public Integer getSampleId()
    {
        return _sampleId;
    }

    public String getSubjectId()
    {
        return _subjectId;
    }

    public Date getSampleDate()
    {
        return _sampleDate;
    }

    public String getPlatform()
    {
        return _platform;
    }

    public String getApplication()
    {
        return _application;
    }

    public void setApplication(String application)
    {
        _application = application;
    }

    public String getInputMaterial()
    {
        return _inputMaterial;
    }

    public void setInputMaterial(String inputMaterial)
    {
        _inputMaterial = inputMaterial;
    }

    public String getName()
    {
        return _name;
    }

    public Integer getInstrumentRunId()
    {
        return _instrumentRunId;
    }

    public Integer getReadsetId()
    {
        return _rowId;
    }

    public void setFileId(Integer fileId)
    {
        _fileId = fileId;
    }

    public void setFileId2(Integer fileId2)
    {
        _fileId2 = fileId2;
    }

    public void setReadsetId(Integer readsetId)
    {
        _rowId = readsetId;
    }

    public String getMid5()
    {
        return _mid5;
    }

    public String getMid3()
    {
        return _mid3;
    }

    public void setMid5(String mid5)
    {
        _mid5 = StringUtils.trimToNull(mid5);
    }

    public void setMid3(String mid3)
    {
        _mid3 = StringUtils.trimToNull(mid3);
    }

    public void setFileName2(String fileName2)
    {
        _fileName2 = fileName2;
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }

    public void setSampleId(Integer sampleId)
    {
        _sampleId = sampleId;
    }

    public void setSubjectId(String subjectId)
    {
        _subjectId = subjectId;
    }

    public void setSampleDate(Date sampleDate)
    {
        _sampleDate = sampleDate;
    }

    public void setPlatform(String platform)
    {
        _platform = platform;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setInstrumentRunId(Integer instrumentRunId)
    {
        _instrumentRunId = instrumentRunId;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }
}
