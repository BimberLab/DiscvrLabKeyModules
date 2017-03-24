package org.labkey.GeneticsCore.analysis;

import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

/**
 * Created by bimber on 3/14/2017.
 */
public interface GFF3Feature extends Feature
{
    Strand getStrand();

    String getType();

    String getName();

    Float getScore();

    String getAttribute(String key);

    boolean hasAttribute(String key);
}
