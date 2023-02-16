package org.labkey.sequenceanalysis.model;

import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.DNASequence;
import org.json.JSONArray;

/**
 * User: bimber
 * Date: 11/25/12
 * Time: 12:03 AM
 */
public class AdapterModel extends SequenceTag
{
    private String _name;
    private String _sequence;
    private boolean _trim5;
    private boolean _trim3;

    public AdapterModel()
    {

    }

    public static AdapterModel fromJSON(JSONArray adapterJson)
    {
        AdapterModel ad = new AdapterModel();
        ad._name = adapterJson.getString(0);
        ad._sequence = adapterJson.getString(1);
        ad._trim5 = adapterJson.getBoolean(2);
        ad._trim3 = adapterJson.getBoolean(3);

        return ad;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getSequence()
    {
        return _sequence;
    }

    public boolean isTrim5()
    {
        return _trim5;
    }

    public boolean isTrim3()
    {
        return _trim3;
    }

    public String getFastaLines()
    {
        StringBuilder sb = new StringBuilder();
        writeSequence(sb, getName());

        return sb.toString();
    }

    private void writeSequence(StringBuilder sb, String name)
    {
        if (_trim5)
        {
            sb.append(">").append(name).append(System.getProperty( "line.separator" ));
            sb.append(getSequence()).append(System.getProperty( "line.separator" ));
        }

        if (_trim3)
        {
            sb.append(">").append(name + "-RC").append(System.getProperty( "line.separator" ));
            try
            {
                DNASequence seq = new DNASequence(getSequence());
                sb.append(seq.getReverseComplement().getSequenceAsString()).append(System.getProperty("line.separator"));
            }
            catch (CompoundNotFoundException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public void setTrim5(boolean trim5)
    {
        _trim5 = trim5;
    }

    public void setTrim3(boolean trim3)
    {
        _trim3 = trim3;
    }
}

