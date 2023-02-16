package org.labkey.sequenceanalysis.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava.nbio.core.sequence.compound.AmbiguityRNACompoundSet;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.biojava.nbio.core.sequence.transcription.TranscriptionEngine;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
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

    private final Map<Integer, String> _sequenceMap = new HashMap<>();

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

        String toTranslate = writer.toString();
        if (StringUtils.isEmpty(toTranslate))
        {
            throw new IllegalStateException("NT string was empty for: " + refNtId);
        }

        DNASequence dna = null;
        Sequence<NucleotideCompound> rnaNts = null;
        Sequence<AminoAcidCompound> aas = null;
        try
        {
            try
            {
                dna = new DNASequence(toTranslate, AmbiguityDNACompoundSet.getDNACompoundSet());
            }
            catch (CompoundNotFoundException e)
            {
                throw new IllegalArgumentException("Improper exon: " + toTranslate, e);
            }

            Sequence<NucleotideCompound> nts = isComplement ? dna.getReverseComplement() : dna;

            //TODO: why NPE??
            //seq:
            rnaNts = _engine.getDnaRnaTranslator().createSequence(nts);
            if (rnaNts == null)
            {
                throw new IllegalArgumentException("Unable to create RNA from: " + dna);
            }

            aas = _engine.getRnaAminoAcidTranslator().createSequence(rnaNts);
            if (aas == null)
            {
                throw new IllegalArgumentException("Unable to create AA from: RNA" + rnaNts + " / DNA: " + dna);
            }

            return aas.getSequenceAsString();
        }
        catch (NullPointerException e)
        {
            _log.error(e.getMessage() == null ? "There was an error" : e.getMessage(), e);
            if (dna != null)
            {
                _log.info("DNA: " + dna);
            }

            if (rnaNts != null)
            {
                _log.info("RNA: " + rnaNts);
            }

            if (aas != null)
            {
                _log.info("AA: " + aas);
            }

            throw e;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTranslation() {
            TranscriptionEngine _engine = new TranscriptionEngine.Builder().dnaCompounds(AmbiguityDNACompoundSet.getDNACompoundSet()).rnaCompounds(AmbiguityRNACompoundSet.getRNACompoundSet()).initMet(false).trimStop(false).build();

            try
            {
                List<String> codons = Arrays.asList("RTA", "RGA");
                for (String toTranslate : codons)
                {
                    DNASequence dna = new DNASequence(toTranslate, AmbiguityDNACompoundSet.getDNACompoundSet());
                    Sequence<NucleotideCompound> nts = dna;

                    Sequence<NucleotideCompound> rnaNts = _engine.getDnaRnaTranslator().createSequence(nts);
                    if (rnaNts == null)
                    {
                        throw new IllegalArgumentException("Unable to create RNA from: " + dna);
                    }

                    Sequence<AminoAcidCompound> aas = _engine.getRnaAminoAcidTranslator().createSequence(rnaNts);
                    if (aas == null)
                    {
                        throw new IllegalArgumentException("Unable to create AA from: RNA" + rnaNts + " / DNA: " + dna);
                    }

                    assertEquals("X", aas.getSequenceAsString());
                }
            }
            catch (CompoundNotFoundException e)
            {
                _log.error(e.getMessage(), e);
            }
        }
    }
    
    public int createExpData(String relPath) {
        PipeRoot pr = PipelineService.get().getPipelineRootSetting(getContainer());
        if (pr == null)
        {
            throw new IllegalArgumentException("Unable to find pipeline root");
        }

        File f = new File(pr.getRootPath(), relPath);
        if (!f.exists())
        {
            throw new IllegalArgumentException("Unable to find file: " + f.getPath());
        }

        ExpData d = ExperimentService.get().getExpDataByURL(f, getContainer());
        if (d != null)
        {
            return d.getRowId();
        }
        
        d = ExperimentService.get().createData(getContainer(), new DataType("Output"));
        d.setDataFileURI(f.toURI());
        d.setName(f.getName());
        d.save(getUser());

        return d.getRowId();
    }
}
