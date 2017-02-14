package org.labkey.sequenceanalysis.query;

/**
 * Created by bimber on 2/8/2017.
 */
public class IPDDisplayColumnFactory extends GenbankDisplayColumnFactory
{
    public IPDDisplayColumnFactory()
    {

    }

    @Override
    protected String getFormattedURL(String v)
    {
        return "https://www.ebi.ac.uk/ipd/mhc/group/NHP/allele/" + v.replaceAll("IPD", "").replaceAll("^(0)+", "");
    }
}
