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
package org.labkey.sequenceanalysis.analysis;

import org.biojava3.core.sequence.DNASequence;
import org.biojava3.core.sequence.compound.AminoAcidCompound;
import org.biojava3.core.sequence.template.Sequence;
import org.biojava3.core.sequence.transcription.DNAToRNATranslator;
import org.biojava3.core.sequence.transcription.RNAToAminoAcidTranslator;
import org.biojava3.core.sequence.transcription.Table;
import org.biojava3.core.sequence.transcription.TranscriptionEngine;
import org.labkey.sequenceanalysis.model.SequenceModel;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 8/18/12
 * Time: 4:10 PM
 */
public class AASnp
{
    private NTSnp _ntSnp;
    private SequenceModel _aaRef;
    private int _aaPosInProtein;
    private int _aaInsertIndex;
    private String _codon;
    private int _frame;
    private String _residueString;
    private String _ntPositionString;

    private static final DNAToRNATranslator _dna2rnaTranslator = TranscriptionEngine.getDefault().getDnaRnaTranslator();
    private static final RNAToAminoAcidTranslator _rnaTranslator = TranscriptionEngine.getDefault().getRnaAminoAcidTranslator();

    public AASnp(NTSnp ntSnp, SequenceModel aaRef, int aaPosInProtein, int aaInsertIndex, String codon, int frame, List<NTSnp> allSnps, byte[] refBases)
    {
        _ntSnp = ntSnp;
        _aaRef = aaRef;
        _aaPosInProtein = aaPosInProtein;
        _aaInsertIndex = aaInsertIndex;
        _codon = codon;
        _frame = frame;

        calculateResidues();
        calculateNtPositionString(allSnps, refBases);
    }

    private void calculateNtPositionString(List<NTSnp> allSnps, byte[] refBases)
    {
        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (NTSnp snp : allSnps)
        {
            sb.append(delim).append(getNtPosString(snp, refBases));
            delim = ",";
        }

        _ntPositionString = sb.toString();
    }

    public String getNtPositionString()
    {
        return _ntPositionString;
    }

    private void calculateResidues()
    {
        if (_codon.length() == 3)
        {
            //in frame deletions translate as 'X' otherwise
            if ("---".equals(_codon))
            {
                _residueString = "-";
            }
            else
            {
                DNASequence dna = new DNASequence(_codon);
                _residueString = _rnaTranslator.createSequence(_dna2rnaTranslator.createSequence(dna)).getSequenceAsString();
                if (_residueString == null || "".equals(_residueString))
                {
                    _residueString = "*";
                }
            }
        }
        else
            _residueString = "?";
    }

    public String getReferenceAaResidue()
    {
        return _aaRef.getSequence().substring(_aaPosInProtein - 1, _aaPosInProtein);
    }

    public int getReferenceAaPosition()
    {
        return _aaPosInProtein;
    }

    public String getReadAaResidue()
    {
        return _residueString == null ? ":" : _residueString;
    }

    public String getCodon()
    {
        return _codon;
    }

    public SequenceModel getAaRef()
    {
        return _aaRef;
    }

    /**
     * @return 1- based NT position relative to the start of the reference NT sequence
     */
    public int getReferenceNtPosition()
    {
        return _ntSnp.getLastRefPosition() == -1 ? -1 : _ntSnp.getLastRefPosition() + 1; //1-based
    }

    public NTSnp getNtSnp()
    {
        return _ntSnp;
    }

    public int getAaInsertIndex()
    {
        return _aaInsertIndex;
    }

    public boolean isSynonymous()
    {
        return getReadAaResidue().equals(getReferenceAaResidue());
    }

    public boolean isInsertion()
    {
        return _aaInsertIndex > 0;
    }

    private String getNtPosString(NTSnp ntSnp, byte[] refSequence)
    {
        return new StringBuilder()
                .append(ntSnp.getReferenceBaseString(refSequence))
                .append((ntSnp.getLastRefPosition() + 1)) //convert to 1-based
                .append((ntSnp.getInsertIndex() == 0 ? "" : "." + ntSnp.getInsertIndex()))
                .append(ntSnp.getReadBaseString()).toString();
    }
}
