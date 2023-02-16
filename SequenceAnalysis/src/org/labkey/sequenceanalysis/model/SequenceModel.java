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
package org.labkey.sequenceanalysis.model;

import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bbimber
 * Date: 8/18/12
 * Time: 2:02 PM
 */
public class SequenceModel
{
    private int rowId;
    private String name;
    private String sequence;
    private byte[] sequenceBases = null;
    private int ref_nt_id;
    private String exons;
    private boolean isComplement = false;

    List<Pair<Integer, Integer>> exonList;

    public SequenceModel()
    {
        MemTracker.getInstance().put(this);
    }

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public boolean isComplement()
    {
        return isComplement;
    }

    public void setIsComplement(Boolean complement)
    {
        isComplement = complement != null && complement;
    }

    public String getSequence()
    {
        return sequence;
    }

    public byte[] getSequenceBases()
    {
        if (sequenceBases != null)
            return sequenceBases;

        sequenceBases = sequence.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        return sequenceBases;
    }

    public void setSequence(String sequence)
    {
        this.sequence = sequence;
    }

    public int getRef_nt_id()
    {
        return ref_nt_id;
    }

    public void setRef_nt_id(int ref_nt_id)
    {
        this.ref_nt_id = ref_nt_id;
    }

    public String getExons()
    {
        return exons;
    }

    public void setExons(String exons)
    {
        this.exons = exons;
        if (exons != null)
        {
            exonList = new ArrayList<>();
            String[] exonsSplit = exons.split(";");
            for (String e : exonsSplit)
            {
                String[] positions = e.split("-");
                Integer start = Integer.parseInt(positions[0]);
                Integer stop = Integer.parseInt(positions[1]);
                exonList.add(Pair.of(start, stop));
            }

            //sort based on exon list.  if sequence is reverse complemented, reverse the order
            //Collections.sort(exonList, new Comparator<Pair<Integer, Integer>>()
            //{
            //    @Override
            //    public int compare(Pair<Integer, Integer> o1, Pair<Integer, Integer> o2)
            //    {
            //        return isComplement ? o2.first.compareTo(o1.first) : o1.first.compareTo(o2.first);
            //    }
            //});

            if (isComplement)
            {
                Collections.reverse(exonList);
            }
        }
    }

    public List<Pair<Integer, Integer>> getExonList()
    {
        return exonList;
    }
}