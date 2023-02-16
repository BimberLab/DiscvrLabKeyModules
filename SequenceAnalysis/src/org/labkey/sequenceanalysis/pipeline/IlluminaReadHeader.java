/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import java.util.regex.Pattern;

/**
* User: jeckels
* Date: 5/29/14
*/
public class IlluminaReadHeader
{
    private final String _instrument;
    private final int _runId;
    private final String _flowCellId;
    private final int _flowCellLane;
    private final int _tileNumber;
    private final int _xCoord;
    private final int _yCoord;
    private final int _pairNumber;
    private boolean _failedFilter;
    private int _controlBits;
    private int _sampleNum;
    private String _indexSequenceString;

    private static final int NO_SAMPLE_NUMBER_FOUND = -1;
    private static final Pattern INDEX_PATTERN_DOUBLE = Pattern.compile("^[ATGCN]+\\+[ATGCN]+$");
    private static final Pattern INDEX_PATTERN_SINGLE = Pattern.compile("^[ATGCN]+$");

    public IlluminaReadHeader(String header) throws IllegalArgumentException
    {
        String[] h;
        int minLength;

        //alternate format: HWI-ST881:298:C15RNACXX:6:2209:15829:47176/1
        if (header.endsWith("/1") || header.endsWith("/2"))
        {
            header = header.replaceAll("/1$", ":1");
            header = header.replaceAll("/2$", ":2");
            minLength = 8;
        }
        else
        {
            minLength = 11;
        }

        h = header.split(":| ");
        if (h.length < minLength)
        {
            throw new IllegalArgumentException("Improperly formatted header: " + header);
        }

        try
        {
            //format 1:
            //@<instrument>:<run number>:<flowcell ID>:<lane>:<tile>:<x-pos>:<y-pos> <read>:<is filtered>:<control number>:<sample number>
            //example: @J00107:108:HF525BBXX:3:1101:1418:1173 1:N:0:14

            //format 2:
            //@<instrument>:<run number>:<flowcell ID>:<lane>:<tile>:<x-pos>:<y-pos> <read>:<is filtered>:<control number>:<index sequence>
            //example: @M00370:191:000000000-B2CPR:1:1101:15020:1351 2:N:0:AAGAGGCA+ACTGCATA

            // another alternate:
            // D00735:242:CBNW7ANXX:2:2201:1228:1813 1:N:0:GTCCGC
            _instrument = h[0];
            _runId = Integer.parseInt(h[1]);
            _flowCellId = h[2];
            _flowCellLane = Integer.parseInt(h[3]);
            _tileNumber = Integer.parseInt(h[4]);
            _xCoord = Integer.parseInt(h[5]);
            _yCoord = Integer.parseInt(h[6]);
            _pairNumber = Integer.parseInt(h[7]);

            if (h.length > 8)
            {
                setFailedFilter(h[8]);
                _controlBits = Integer.parseInt(h[9]);
            }

            _sampleNum = NO_SAMPLE_NUMBER_FOUND;
            _indexSequenceString = null;
            if (h.length > 10)
            {
                //Note: if this read was not demultiplexed by illumina, the index sequence may appear in this position
                if (INDEX_PATTERN_DOUBLE.matcher(h[10]).find() || INDEX_PATTERN_SINGLE.matcher(h[10]).find())
                {
                    _indexSequenceString = h[10];
                }
                else
                {
                    try
                    {
                        _sampleNum = Integer.parseInt(h[10]);
                    }
                    catch (NumberFormatException e)
                    {
                        throw new IllegalArgumentException("Improper sample index: " + h[10] + ", full header: " + header);
                    }
                }
            }
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Improper header: " + header);
        }
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public int getRunId()
    {
        return _runId;
    }

    public String getFlowCellId()
    {
        return _flowCellId;
    }

    public int getFlowCellLane()
    {
        return _flowCellLane;
    }

    public int getTileNumber()
    {
        return _tileNumber;
    }

    public int getxCoord()
    {
        return _xCoord;
    }

    public int getyCoord()
    {
        return _yCoord;
    }

    public int getPairNumber()
    {
        return _pairNumber;
    }

    public boolean isFailedFilter()
    {
        return _failedFilter;
    }

    public void setFailedFilter(boolean failedFilter)
    {
        _failedFilter = failedFilter;
    }

    public void setFailedFilter(String failedFilter)
    {
        _failedFilter = "Y".equals(failedFilter);
    }

    public int getControlBits()
    {
        return _controlBits;
    }

    /**
     * @return Sample index, as assigned by illumina.  NO_SAMPLE_NUMBER_FOUND indicates this is part of non-assigned reads.
     */
    public int getSampleNum()
    {
        return _sampleNum;
    }

    public boolean hasSampleNum()
    {
        return _sampleNum != NO_SAMPLE_NUMBER_FOUND;
    }

    public String getIndexSequenceString()
    {
        return _indexSequenceString;
    }
}
