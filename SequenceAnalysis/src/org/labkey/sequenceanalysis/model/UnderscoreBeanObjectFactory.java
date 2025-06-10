package org.labkey.sequenceanalysis.model;

import org.labkey.api.data.BeanObjectFactory;

/**
 * Created by bimber on 2/20/2015.
 */
public class UnderscoreBeanObjectFactory<K> extends BeanObjectFactory<K>
{
    public UnderscoreBeanObjectFactory(Class<K> clss)
    {
        super(clss);
    }

    @Override
    public String convertToPropertyName(String name)
    {
        name = super.convertToPropertyName(name);

        if (name != null)
        {
            name = name.replaceAll("_", "");
        }

        return name;
    }
}
