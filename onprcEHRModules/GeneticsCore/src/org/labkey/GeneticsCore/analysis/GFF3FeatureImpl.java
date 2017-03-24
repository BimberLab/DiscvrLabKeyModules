package org.labkey.GeneticsCore.analysis;

import htsjdk.tribble.annotation.Strand;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 3/14/2017.
 */
public class GFF3FeatureImpl implements GFF3Feature
{
    private Strand _strand;
    private String _type;
    private String _contig;
    private String _source;
    private Integer _phase;
    private int _start;
    private int _end;
    private String _name;
    private Float _score;
    private Map<String, String> _attributes =  new HashMap<>();

    public GFF3FeatureImpl(String contig, int start, int end)
    {
        _contig = contig;
        _start = start;
        _end = end;
    }

    @Override
    public Strand getStrand()
    {
        return _strand;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public Float getScore()
    {
        return _score;
    }

    @Override
    public String getAttribute(String key)
    {
        return _attributes.get(key);
    }

    @Override
    public boolean hasAttribute(String key)
    {
        return _attributes.containsKey(key);
    }

    @Override
    public String getContig()
    {
        return _contig;
    }

    @Override
    public int getStart()
    {
        return _start;
    }

    @Override
    public int getEnd()
    {
        return _end;
    }

    public void setStrand(Strand strand)
    {
        _strand = strand;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public void setContig(String contig)
    {
        _contig = contig;
    }

    public void setStart(int start)
    {
        _start = start;
    }

    public void setEnd(int end)
    {
        _end = end;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setScore(Float score)
    {
        _score = score;
    }

    public String getSource()
    {
        return _source;
    }

    public void setSource(String source)
    {
        _source = source;
    }

    public Integer getPhase()
    {
        return _phase;
    }

    public void setPhase(Integer phase)
    {
        _phase = phase;
    }

    public void addAttributes(Map<String, String> attributes)
    {
        _attributes.putAll(attributes);
    }
}
