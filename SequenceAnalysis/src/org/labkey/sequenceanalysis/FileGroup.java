package org.labkey.sequenceanalysis;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by bimber on 2/19/2015.
 */
public class FileGroup implements Serializable
{
    public String name;
    public List<FilePair> filePairs = new ArrayList<>();

    public Set<String> getPlatformUnits()
    {
        Set<String> ret = new HashSet<>();
        for (FilePair p : filePairs)
        {
            ret.add(p.platformUnit);
        }

        return ret;
    }

    public List<List<FilePair>> groupByPlatformUnit()
    {
        List<List<FilePair>> ret = new ArrayList<>();
        Map<String, List<FilePair>> grouped = new TreeMap<>();

        for (FilePair pair : filePairs)
        {
            if (pair.platformUnit == null)
            {
                ret.add(List.of(pair));
            }
            else
            {
                if (!grouped.containsKey(pair.platformUnit))
                {
                    grouped.put(pair.platformUnit, new ArrayList<>());
                }

                grouped.get(pair.platformUnit).add(pair);
            }
        }

        for (String key : grouped.keySet())
        {
            grouped.get(key).sort(Comparator.comparing(filePair -> filePair.file1));
        }

        ret.addAll(grouped.values());

        return ret;
    }

    public static class FilePair implements Serializable
    {
        public String platformUnit;
        public String centerName;
        public File file1;
        public File file2;
    }

    public boolean isPaired()
    {
        for (FileGroup.FilePair fp : filePairs)
        {
            if (fp.file2 != null)
            {
                return true;
            }
        }

        return false;
    }
}
