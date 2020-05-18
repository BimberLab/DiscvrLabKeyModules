package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.FileUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bimber on 1/9/2015.
 *
 * Designed to take a user-generated chain file and prepare it for the sequence module.
 * It will perform general validation/cleanup on the file, since HTS-JDK is more strict than other tools.
 * This primarily involves removing comments and ensuring chainIds are unique
 *
 */
public class ChainFileValidator
{
    public ChainFileValidator()
    {

    }

    public File validateChainFile(File chainFile, int sourceGenome, int targetGenome) throws IllegalArgumentException
    {
        // known issues are:
        // HTS-JDK does not accept comment lines
        // chainIds must be unique
        // reference names must match those in the DB for that genome

        Pattern SPLITTER = Pattern.compile("\\s");
        Set<Integer> uniqueIds = new HashSet<>();
        Set<Integer> encounteredIds = new HashSet<>();

        //step 1: iterate lines, gather unique chain IDs
        boolean hasChanges = false;
        Map<String, String> sourceTranslations = new HashMap<>();
        Map<String, String> targetTranslations = new HashMap<>();
        try (BufferedLineReader reader = new BufferedLineReader(IOUtil.openFileForReading(chainFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.equals("") || line.startsWith("#"))
                {
                    continue;
                }

                if (line.startsWith("chain"))
                {
                    String[] chainFields = SPLITTER.split(line);
                    if (chainFields.length != 13)
                    {
                        throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": chain header should have 13 fields");
                    }

                    try
                    {
                        int chainId = Integer.parseInt(chainFields[12]);
                        if (uniqueIds.contains(chainId))
                        {
                            hasChanges = true;
                        }
                        uniqueIds.add(chainId);

                        //validate source refName
                        String sourceSeq = chainFields[2];
                        String sourceResolved = resolveSequenceId(sourceSeq, sourceGenome);
                        if (sourceResolved == null)
                        {
                            throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": unable to resolve sequence with name " + sourceSeq + " in reference genome: " + sourceGenome + ".  There names of the contigs in the chain file must match the names used for the sequences within the site.");
                        }
                        if (!sourceSeq.equals(sourceResolved))
                        {
                            sourceTranslations.put(sourceSeq, sourceResolved);
                        }

                        //and targetRefName
                        String targetSeq = chainFields[7];
                        String targetResolved = resolveSequenceId(targetSeq, targetGenome);
                        if (targetResolved == null)
                        {
                            throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": unable to resolve sequence with name " + targetSeq + " in reference genome: " + targetGenome + ".  There names of the contigs in the chain file must match the names used for the sequences within the site.");
                        }
                        if (!targetSeq.equals(targetResolved))
                        {
                            targetTranslations.put(targetSeq, targetResolved);
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": chain Id should be an integer: " + e.getMessage());
                    }
                }
            }
        }

        //step 2: iterate lines, write new file
        if (!hasChanges && sourceTranslations.isEmpty() && targetTranslations.isEmpty())
        {
            //return original
            return chainFile;
        }
        else
        {
            File output = new File(chainFile.getParentFile(), FileUtil.getBaseName(chainFile) + "-cleaned." + FileUtil.getExtension(chainFile));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output, true)))
            {
                try (BufferedLineReader reader = new BufferedLineReader(IOUtil.openFileForReading(chainFile)))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        line = StringUtils.trimToEmpty(line);
                        if (line.startsWith("chain"))
                        {
                            String[] chainFields = SPLITTER.split(line);

                            try
                            {
                                int chainId = Integer.parseInt(chainFields[12]);
                                if (encounteredIds.contains(chainId))
                                {
                                    chainId = getNextChainId(chainId, uniqueIds);
                                }

                                encounteredIds.add(chainId);
                                chainFields[12] = String.valueOf(chainId);
                            }
                            catch (NumberFormatException e)
                            {
                                throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": chain Id should be an integer: " + chainFields[12]);
                            }

                            String sourceSeq = chainFields[2];
                            if (sourceTranslations.containsKey(sourceSeq))
                            {
                                chainFields[2] = sourceTranslations.get(sourceSeq);
                            }

                            String targetSeq = chainFields[7];
                            if (targetTranslations.containsKey(targetSeq))
                            {
                                chainFields[7] = targetTranslations.get(targetSeq);
                            }

                            writer.write(StringUtils.join(chainFields, " "));
                            writer.write('\n');
                        }
                        else
                        {
                            writer.write(line);
                            writer.write('\n');
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException(e);
            }

            //one more verification
            new LiftOver(output);

            return output;
        }
    }

    private int getNextChainId(Integer chainId, Set<Integer> uniqueIds)
    {
        Integer newId = chainId;
        while (uniqueIds.contains(newId))
        {
            newId++;
        }

        uniqueIds.add(newId);

        return newId;
    }

    private Map<Integer, Map<String, String>> _cachedReferencesByGenome = new HashMap<>();

    private String resolveSequenceId(String refName, int genomeId)
    {
        if (!_cachedReferencesByGenome.containsKey(genomeId))
        {
            final Map<String, String> cachedReferences = new CaseInsensitiveHashMap<>();
            SqlSelector ss = new SqlSelector(DbScope.getLabKeyScope(), new SQLFragment("SELECT r.rowid, r.name, r.genbank, r.refSeqId FROM sequenceanalysis.ref_nt_sequences r WHERE r.rowid IN (SELECT ref_nt_id FROM sequenceanalysis.reference_library_members m WHERE m.library_id = ?) ", genomeId));
            ss.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    cachedReferences.put(rs.getString("name"), rs.getString("name"));

                    if (StringUtils.trimToNull(rs.getString("genbank")) != null)
                    {
                        cachedReferences.put(rs.getString("genbank"), rs.getString("name"));
                    }

                    if (StringUtils.trimToNull(rs.getString("refSeqId")) != null)
                    {
                        cachedReferences.put(rs.getString("refSeqId"), rs.getString("name"));
                    }
                }
            });

            _cachedReferencesByGenome.put(genomeId, cachedReferences);
        }

        Map<String, String> cachedReferences = _cachedReferencesByGenome.get(genomeId);

        if (cachedReferences.containsKey(refName))
        {
            return cachedReferences.get(refName);
        }

        //UCSC is a main source of chain files, so deal with their quirks:
        if (refName.startsWith("chr"))
        {
            String toTest = refName.replaceFirst("chr", "");
            if (cachedReferences.containsKey(toTest))
            {
                return cachedReferences.get(toTest);
            }
        }

        refName = translateUcscUnplaced(refName);

        return cachedReferences.get(refName);
    }

    private final static Pattern _versionEnd = Pattern.compile("v[0-9]+$");

    private static String translateUcscUnplaced(String refName)
    {
        // They renamed unplaced contigs, i.e.:
        // NW_021160495.1 -> chrUn_NW_021160495v1
        // NW_021160383.1 -> chrX_NW_021160383v1_random
        if (refName.endsWith("_random"))
        {
            refName = refName.replaceAll("_random$", "");
        }

        if (refName.startsWith("chr") && refName.contains("_"))
        {
            String[] tokens = refName.split("_");
            refName = StringUtils.join(Arrays.copyOfRange(tokens, 1, tokens.length), "_");
        }

        Matcher m = _versionEnd.matcher(refName);
        if (m.find())
        {
            refName = m.replaceAll(matchResult -> {
                return "." + matchResult.group().substring(1);
            });
        }

        return refName;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTranslation() throws Exception
        {
            assertEquals("NW_021160495.1", translateUcscUnplaced("chrUn_NW_021160495v1"));
            assertEquals("NW_021160383.1", translateUcscUnplaced("chrX_NW_021160383v1_random"));
        }
    }
}

