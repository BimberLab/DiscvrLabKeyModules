package org.labkey.sequenceanalysis.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.biojava3.core.sequence.DNASequence;
import org.biojava3.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava3.core.sequence.compound.AmbiguityRNACompoundSet;
import org.biojava3.core.sequence.compound.NucleotideCompound;
import org.biojava3.core.sequence.template.Sequence;
import org.biojava3.core.sequence.transcription.TranscriptionEngine;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 7/27/2014.
 */
public class SequenceTriggerHelper
{
    private Container _container = null;
    private User _user = null;
    private static final Logger _log = LogManager.getLogger(SequenceTriggerHelper.class);
    private TableInfo _refNts = null;

    private static final TranscriptionEngine _engine = new TranscriptionEngine.Builder().dnaCompounds(AmbiguityDNACompoundSet.getDNACompoundSet()).rnaCompounds(AmbiguityRNACompoundSet.getRNACompoundSet()).initMet(false).trimStop(false).build();

    private Map<Integer, String> _sequenceMap = new HashMap<>();

    public SequenceTriggerHelper(int userId, String containerId)
    {
        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new RuntimeException("User does not exist: " + userId);

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new RuntimeException("Container does not exist: " + containerId);

    }

    private User getUser()
    {
        return _user;
    }

    private Container getContainer()
    {
        return _container;
    }

    public void addSequence(int rowIdx, String sequence)
    {
        if (sequence != null)
        {
            _sequenceMap.put(rowIdx, sequence);
        }
    }

    private TableInfo getRefNts()
    {
        if (_refNts != null)
        {
            return _refNts;
        }

        _refNts = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        return _refNts;
    }

    public void processSequence(int rowId, String sequence) throws IOException
    {
        if (sequence != null)
        {
            RefNtSequenceModel model = new TableSelector(getRefNts(), new SimpleFilter(FieldKey.fromString("rowid"), rowId), null).getObject(RefNtSequenceModel.class);
            model.createFileForSequence(getUser(), sequence, null);

        }
    }

    public String extractAASequence(int refNtId, List<List<Number>> exons, boolean isComplement)
    {
        RefNtSequenceModel model = RefNtSequenceModel.getForRowId(refNtId);
        if (model == null)
        {
            return null;
        }

        StringWriter writer = new StringWriter();
        for (List<Number> exon : exons)
        {
            if (exon.size() != 2)
            {
                throw new IllegalArgumentException("Improper exon: " + StringUtils.join(exon, "-"));
            }

            //exons supplied as 1-based coordinates
            try
            {
                model.writeSequence(writer, -1, exon.get(0).intValue(), exon.get(1).intValue());
            }
            catch (IOException e)
            {
                _log.error(e);
                return null;
            }
        }

        if (writer.getBuffer().length() > 0)
        {
            DNASequence dna = new DNASequence(writer.getBuffer().toString(), AmbiguityDNACompoundSet.getDNACompoundSet());
            Sequence<NucleotideCompound> toTranslate = isComplement ? dna.getReverseComplement() : dna;

            return _engine.getRnaAminoAcidTranslator().createSequence(_engine.getDnaRnaTranslator().createSequence(toTranslate)).getSequenceAsString();
        }

        return null;
    }
}
