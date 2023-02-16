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
package org.labkey.sequenceanalysis.run.util;


import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;

/**
 * User: bbimber
 * Date: 8/20/12
 * Time: 7:59 AM
 */
public class NTSnp
{
    private final CigarPositionIterable.PositionInfo _pi;
    private Integer _insertIndex = null;
    private String _flag;
    private boolean _flagSet;

    public NTSnp(CigarPositionIterable.PositionInfo pi)
    {
        _pi = pi;
    }

    /**
     * 0-based, -1 indicates an insertion
     * @return
     */
    public int getReferencePosition()
    {
        return _pi.getRefPosition();
    }

    /**
     * 0-based, -1 indicates a deletion
     * @return
     */
    public int getReadPosition()
    {
        return _pi.getReadPosition();
    }

    public int getIndel()
    {
        return _pi.getIndel();
    }

    /**
     * @return The length of the insertion at this position.  For non-indels or deletions, it will be zero.  The first position of an insertion is 0 (ie. the base overlapping with the references), the second is 1, etc.
     */
    public int getInsertIndex()
    {
        return _insertIndex == null ? _pi.getInsertIndex() : _insertIndex;
    }

    public void setInsertIndex(int index)
    {
        _insertIndex = index;
    }

    public int getLastReadPosition()
    {
        return _pi.getLastReadPosition();
    }

    /**
     * Returns the last reference position that was overlapped (0-based).  Primarily used for insertions relative to the reference,
     * in order to find the previous reference position.  For non-insertions, this will return the same value as getRefPosition()
     * @return
     */
    public int getLastRefPosition()
    {
        return _pi.getLastRefPosition();
    }

    public byte getReferenceBase(byte[] referenceBases)
    {
        return _pi.getReferenceBase(referenceBases);
    }

    public String getReferenceBaseString(byte[] referenceBases)
    {
        return Character.toString((char)getReferenceBase(referenceBases));
    }

    public byte getReadBase()
    {
        return _pi.getReadBase();
    }

    public String getReadBaseString()
    {
        return Character.toString((char)getReadBase());
    }

    public String getReadname()
    {
        return _pi.getRecord().getReadName();
    }

    public boolean isIndel()
    {
        return isDel() || isInsertion();
    }

    public boolean isInsertion()
    {
        return _pi.isInsertion();
    }

    public boolean isDel()
    {
        return _pi.isDel();
    }

    public String getFlag()
    {
        return _flag;
    }

    public void setFlag(String flag)
    {
        _flag = flag;
        _flagSet = true;
    }

    public boolean isFlagSet()
    {
        return _flagSet;
    }

    public CigarPositionIterable.PositionInfo getPositionInfo()
    {
        return _pi;
    }

    public int getBaseQuality()
    {
        return _pi.getBaseQuality();
    }

    public String getReferenceName()
    {
        return _pi.getReferenceName();
    }
}
