package org.labkey.snprc_scheduler.security;

import org.jetbrains.annotations.Nullable;

/**
 * Created by thawkins on 11/2/2018.
 */


public enum QCStateEnum
{
    // modeled after SND QCStates
    COMPLETED(1, "Completed", "Record has been completed and is public", true),
    REJECTED(2, "Rejected", "Record has been reviewed and rejected", false),
    REVIEW_REQUIRED(3, "Review Required", "Review is required prior to public release", false),
    IN_PROGRESS(4, "In Progress", "Draft Record, not public", false);

    private int _value;
    private String _name;
    private String _description;
    private boolean _publicData;

    QCStateEnum(int value, String name, String description, boolean publicData)
    {
        _value = value;
        _name = name;
        _description = description;
        _publicData = publicData;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isPublicData()
    {
        return _publicData;
    }

    public void setPublicData(boolean publicData)
    {
        _publicData = publicData;
    }

    public int getValue()
    {
        return _value;
    }

    public void setValue(int value)
    {
        _value = value;
    }


    public static Integer getValueByName (String name)
    {
        for (QCStateEnum qcStateEnum : QCStateEnum.values())
        {
            if (qcStateEnum.getName().toLowerCase().equals(name.toLowerCase()))
            {
                return qcStateEnum.getValue();
            }
        }

        return null;
    }

    @Nullable
    public static QCStateEnum getByName(String name)
    {
        for (QCStateEnum qcStateEnum : QCStateEnum.values())
        {
            if (qcStateEnum.getName().toLowerCase().equals(name.toLowerCase()))
            {
                return qcStateEnum;
            }
        }

        return null;
    }

}
