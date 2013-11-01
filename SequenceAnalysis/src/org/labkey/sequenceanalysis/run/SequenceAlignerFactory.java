package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;

/**
 * User: bimber
 * Date: 10/28/13
 * Time: 6:41 PM
 */
public class SequenceAlignerFactory
{
    private Class<? extends AlignerWrapper> _clazz;
    private String _name;

    public SequenceAlignerFactory(Class<? extends AlignerWrapper> clazz, String name)
    {
        _clazz = clazz;
        _name = name;
    }

    public AlignerWrapper create(Logger log) throws IllegalArgumentException
    {
        try
        {
            return _clazz.getDeclaredConstructor(_clazz).newInstance(log);
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalArgumentException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalArgumentException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new IllegalArgumentException(e);
        }
        catch (InstantiationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }
}
