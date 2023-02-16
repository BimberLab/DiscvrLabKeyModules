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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava.nbio.core.sequence.compound.AmbiguityRNACompoundSet;
import org.biojava.nbio.core.sequence.transcription.TranscriptionEngine;
import org.labkey.sequenceanalysis.model.SequenceModel;

import java.util.List;

/**
 * User: bbimber
 * Date: 8/18/12
 * Time: 4:10 PM
 */
public class AASnp
{
    private static final Logger _log = LogManager.getLogger(AASnp.class);

    private final NTSnp _ntSnp;
    private final SequenceModel _aaRef;
    private final int _aaPosInProtein;
    private final int _aaInsertIndex;
    private final String _codon;
    private final int _frame;
    private String _residueString;
    private String _ntPositionString;

    private static final TranscriptionEngine _engine = new TranscriptionEngine.Builder().dnaCompounds(AmbiguityDNACompoundSet.getDNACompoundSet()).rnaCompounds(AmbiguityRNACompoundSet.getRNACompoundSet()).initMet(false).build();

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
                try
                {
                    DNASequence dna = new DNASequence(_codon, AmbiguityDNACompoundSet.getDNACompoundSet());
                    _residueString = _engine.getRnaAminoAcidTranslator().createSequence(_engine.getDnaRnaTranslator().createSequence(dna)).getSequenceAsString();
                }
                catch (Exception e)
                {
                    _log.error("unable to translate string: " + _codon, e);
                }

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
        return ntSnp.getReferenceBaseString(refSequence) +
                (ntSnp.getLastRefPosition() + 1) + //convert to 1-based
                (ntSnp.getInsertIndex() == 0 ? "" : "." + ntSnp.getInsertIndex()) +
                ntSnp.getReadBaseString();
    }
}
