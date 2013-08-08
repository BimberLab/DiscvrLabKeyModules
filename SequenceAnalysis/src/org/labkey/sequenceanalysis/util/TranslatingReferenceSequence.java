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
package org.labkey.sequenceanalysis.util;

import org.apache.log4j.Logger;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.analysis.AASnp;
import org.labkey.sequenceanalysis.analysis.NTSnp;
import org.labkey.sequenceanalysis.model.SequenceModel;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/30/12
 * Time: 8:09 PM
 */
public class TranslatingReferenceSequence
{
    int _id;
    SequenceModel _nt;
    SequenceModel[] _peptides;

    private final static Logger _log = Logger.getLogger(TranslatingReferenceSequence.class);

    public TranslatingReferenceSequence(int id)
    {
        _id = id;
        queryDb();
    }

    private void queryDb()
    {
        TableInfo tableNt = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        SimpleFilter ntFilter = new SimpleFilter(FieldKey.fromString("rowid"), _id);
        TableSelector tsNt = new TableSelector(tableNt, Table.ALL_COLUMNS, ntFilter, null);
        SequenceModel[] nts = tsNt.getArray(SequenceModel.class);
        if (nts == null || nts.length == 0)
            throw new RuntimeException("Unable to find NT Reference sequence with RowId: " + _id);

        _nt = nts[0];

        //then cache AA records
        TableInfo tableAa = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES);
        SimpleFilter aaFilter = new SimpleFilter(FieldKey.fromString("ref_nt_id"), _id);
        TableSelector ts = new TableSelector(tableAa, Table.ALL_COLUMNS, aaFilter, null);
        _peptides = ts.getArray(SequenceModel.class);
    }

    /**
     *
     * @param readSnps
     * @return
     */
    public List<AASnp> translateSnpsForRead(Map<Integer, List<NTSnp>> readSnps)
    {
        List<AASnp> snps = new ArrayList<>();
        for (Integer readIdx : readSnps.keySet())
        {
            List<NTSnp> positions = readSnps.get(readIdx);
            for (NTSnp pi : positions)
            {
                snps.addAll(translateSnp(pi, readSnps));
            }
        }
        return snps;
    }

    private List<AASnp> translateSnp(NTSnp pi, Map<Integer, List<NTSnp>> readSnps)
    {
        List<AASnp> snps = new ArrayList<>();
        for (SequenceModel model : _peptides)
        {
            byte[] refBases = _nt.getSequenceBases();
            int rPos0 = pi.getLastRefPosition(); //0-based
            int rPos1 = rPos0 + 1; //1-based

            int peptideLength = model.getSequenceBases().length;
            int ntOffset = 0;
            for (Pair<Integer, Integer> pair : model.getExonList())
            {
                if (pair.first <= rPos1 && pair.second >= rPos1)
                {
                    int ntPosInProtein;
                    int aaPosInProtein;
                    if (model.isComplement())
                    {
                        ntPosInProtein = pair.second - rPos1 + ntOffset; //0-based
                    }
                    else
                    {
                        ntPosInProtein = rPos1 - pair.first + ntOffset; //0-based
                    }

                    aaPosInProtein = (int)Math.floor(ntPosInProtein / 3) + 1; //1-based

                    if (aaPosInProtein > peptideLength)
                    {
                        _log.error("AA Position exceeds peptide length for peptide " + model.getName() + ": " + aaPosInProtein + " / " + peptideLength);
                    }


                    Integer[] positions = new Integer[3];
                    int frame = ntPosInProtein % 3;
                    if (model.isComplement())
                    {
                        switch (frame)
                        {
                            case 2:
                                positions[0] = rPos1 + 2;
                                positions[1] = rPos1 + 1;
                                positions[2] = rPos1;
                                break;
                            case 1:
                                positions[2] = rPos1 + 1;
                                positions[1] = rPos1;
                                positions[0] = rPos1 - 1;
                                break;
                            case 0:
                                positions[0] = rPos1;
                                positions[1] = rPos1 - 1;
                                positions[2] = rPos1 - 2;
                                break;
                        }
                    }
                    else
                    {
                        switch (frame)
                        {
                            case 0:
                                positions[0] = rPos1;
                                positions[1] = rPos1 + 1;
                                positions[2] = rPos1 + 2;
                                break;
                            case 1:
                                positions[0] = rPos1 - 1;
                                positions[1] = rPos1;
                                positions[2] = rPos1 + 1;
                                break;
                            case 2:
                                positions[0] = rPos1 - 2;
                                positions[1] = rPos1 - 1;
                                positions[2] = rPos1;
                                break;
                        }
                    }

                    StringBuilder codon = new StringBuilder();

                    //to track the SNPs that comprise this codon, 0-based
                    Map<Integer, NTSnp> snpMap = new HashMap<Integer, NTSnp>();
                    Integer positionInCodon = null; //0-based
                    for (Integer p : positions) //1-based
                    {
                        int position = p - 1; //convert to 0-based
                        if (readSnps.containsKey(position))
                        {
                            StringBuilder thisSegment = new StringBuilder();
                            int positionInSegment = 0;
                            Map<Integer, NTSnp> snpMapForSegment = new HashMap<Integer, NTSnp>(); //0-based

                            //force sorting by idx
                            List<NTSnp> otherSnps = readSnps.get(position);
                            Map<Integer, NTSnp> idxMap = new TreeMap<>();
                            for (NTSnp snp : otherSnps)
                            {
                                idxMap.put(snp.getInsertIndex(), snp);
                            }

                            if (!idxMap.containsKey(0))
                            {
                                thisSegment.append((char)refBases[position]); //0-based
                            }

                            for (Integer idx : idxMap.keySet())
                            {
                                NTSnp otherSnp = idxMap.get(idx);
                                snpMapForSegment.put(thisSegment.length(), otherSnp);
                                thisSegment.append((char)otherSnp.getReadBase());

                                if (otherSnp.equals(pi))
                                {
                                    positionInSegment = thisSegment.length() - 1;
                                }
                            }

                            if (model.isComplement())
                            {
                                thisSegment = thisSegment.reverse();
                                positionInSegment = thisSegment.length() - positionInSegment;

                                for (Integer i : snpMapForSegment.keySet())
                                {
                                    snpMap.put(((thisSegment.length() - i) + codon.length()), snpMapForSegment.get(i));
                                }
                            }
                            else
                            {
                                for (Integer i : snpMapForSegment.keySet())
                                {
                                    snpMap.put((i + codon.length()), snpMapForSegment.get(i));
                                }
                            }

                            positionInCodon = codon.length() + positionInSegment;
                            codon.append(thisSegment);
                        }
                        else
                        {
                            codon.append((char)refBases[position]); //0-based
                        }
                    }

                    //NOTE: if the codon is 4 or fewer bases, treat as a single SNP.  otherwise split into multiples
                    assert positionInCodon != null;
                    if (codon.length() <= 4)
                    {
                        List<NTSnp> snpList = new ArrayList<NTSnp>(snpMap.values());
                        snps.add(new AASnp(pi, model, aaPosInProtein, 0, codon.toString(), frame, snpList, _nt.getSequenceBases()));
                    }
                    else
                    {
                        int aaInsertIndex = ((Double)Math.floor(positionInCodon / 3)).intValue();
                        int start = aaInsertIndex * 3;
                        int stop = (codon.length() - start) <= 4 ? codon.length() : start + 3;
                        String tmpCodon = codon.substring(start, stop);
                        List<NTSnp> snpList = new ArrayList<NTSnp>();
                        for (int j = start; j<stop; j++)
                        {
                            if (snpMap.containsKey(j))
                                snpList.add(snpMap.get(j));
                        }
                        snps.add(new AASnp(pi, model, aaPosInProtein, aaInsertIndex, tmpCodon, frame, snpList, _nt.getSequenceBases()));
                    }
                }

                ntOffset += (pair.second - pair.first) + 1;
            }
        }

        return snps;
    }
}
