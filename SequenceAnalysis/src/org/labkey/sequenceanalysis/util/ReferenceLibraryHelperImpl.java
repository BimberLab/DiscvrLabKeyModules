package org.labkey.sequenceanalysis.util;

import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 7/28/2014.
 */
public class ReferenceLibraryHelperImpl implements ReferenceLibraryHelper
{
    private File _refFasta;
    private Map<String, Integer> _cachedIds = new HashMap<>();

    public ReferenceLibraryHelperImpl(File refFasta)
    {
        _refFasta = refFasta;
    }

    @Override
    public File getReferenceFasta()
    {
        return _refFasta;
    }

    @Override
    public File getIdKeyFile()
    {
        return new File(getReferenceFasta().getParentFile(), FileUtil.getBaseName(getReferenceFasta()) + ".idKey.txt");
    }

    @Override
    public File getIndexFile()
    {
        return new File(getReferenceFasta().getParentFile(), getReferenceFasta().getName() + ".fai");
    }

    @Override
    public Integer resolveSequenceId(String refName)
    {
        if (_cachedIds.containsKey(refName))
        {
            return _cachedIds.get(refName);
        }

        File idKey = getIdKeyFile();
        if (idKey.exists())
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(idKey)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] tokens = line.split("\t");
                    if (tokens.length > 1 && refName.equals(tokens[1]))
                    {
                        _cachedIds.put(refName, Integer.parseInt(tokens[0]));

                        return _cachedIds.get(refName);
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        //for legacy libraries...
        String[] keys = refName.split("\\|");
        Integer ret = null;

        if (keys.length != 2)
        {
            _cachedIds.put(refName, null);
            return null;
        }

        _cachedIds.put(refName, Integer.parseInt(keys[0]));
        return _cachedIds.get(refName);
    }
}