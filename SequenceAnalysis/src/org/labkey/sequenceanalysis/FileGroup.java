package org.labkey.sequenceanalysis;

import org.json.JSONObject;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<String, List<FilePair>> grouped = new HashMap<>();

        for (FilePair pair : filePairs)
        {
            if (pair.platformUnit == null)
            {
                ret.add(Arrays.asList(pair));
            }
            else
            {
                if (!grouped.containsKey(pair.platformUnit))
                {
                    grouped.put(pair.platformUnit, new ArrayList<FilePair>());
                }

                grouped.get(pair.platformUnit).add(pair);
            }
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
