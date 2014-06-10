/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust.model;

import org.labkey.api.data.Entity;

/**
 * User: klum
 * Date: 2/18/13
 */
public class SamplePickup extends Entity
{
    private int _rowId;
    private String _name;
    private String _description;
    private boolean _arrangeForPickup;
    private boolean _holdOvernight;
    private int _pickupContact;
    private String _sampleTestLocation;
    private String _labBuilding;
    private String _labRoom;

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
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

    public boolean isArrangeForPickup()
    {
        return _arrangeForPickup;
    }

    public void setArrangeForPickup(boolean arrangeForPickup)
    {
        _arrangeForPickup = arrangeForPickup;
    }

    public boolean isHoldOvernight()
    {
        return _holdOvernight;
    }

    public void setHoldOvernight(boolean holdOvernight)
    {
        _holdOvernight = holdOvernight;
    }

    public int getPickupContact()
    {
        return _pickupContact;
    }

    public void setPickupContact(int pickupContact)
    {
        _pickupContact = pickupContact;
    }

    public String getSampleTestLocation()
    {
        return _sampleTestLocation;
    }

    public void setSampleTestLocation(String sampleTestLocation)
    {
        _sampleTestLocation = sampleTestLocation;
    }

    public String getLabBuilding()
    {
        return _labBuilding;
    }

    public void setLabBuilding(String labBuilding)
    {
        _labBuilding = labBuilding;
    }

    public String getLabRoom()
    {
        return _labRoom;
    }

    public void setLabRoom(String labRoom)
    {
        _labRoom = labRoom;
    }
}
