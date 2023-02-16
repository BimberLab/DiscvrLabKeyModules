package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    public File processChainFile(File chainFile, int sourceGenome, int targetGenome, boolean allowUnknownContigs, List<String> messages) throws IllegalArgumentException
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
        Set<String> sourceBlacklist = new HashSet<>();
        Set<String> targetBlacklist = new HashSet<>();

        Set<String> unknownSource = new HashSet<>();
        Set<String> unknownTarget = new HashSet<>();
        try (BufferedLineReader reader = new BufferedLineReader(IOUtil.openFileForReading(chainFile)))
        {
            String line;
            List<String> errors = new ArrayList<>();
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
                            if (allowUnknownContigs)
                            {
                                sourceBlacklist.add(sourceSeq);
                            }
                            else
                            {
                                unknownSource.add(sourceSeq);
                            }
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
                            if (allowUnknownContigs)
                            {
                                targetBlacklist.add(targetSeq);
                            }
                            else
                            {
                                unknownTarget.add(targetSeq);
                            }
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

            if (!unknownSource.isEmpty() || !unknownTarget.isEmpty())
            {
                if (!unknownSource.isEmpty())
                {
                    errors.add("Unable to resolve contigs in source genome: " + StringUtils.join(unknownSource, "; "));
                }

                if (!unknownTarget.isEmpty())
                {
                    errors.add("Unable to resolve contigs in target genome: " + StringUtils.join(unknownTarget, "; "));
                }

                throw new IllegalArgumentException(StringUtils.join(errors, ",\n"));
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
            int totalChainSkipped = 0;
            int totalChainIdUpdated = 0;
            int totalPassing = 0;
            File output = new File(chainFile.getParentFile(), FileUtil.getBaseName(chainFile) + "-cleaned." + FileUtil.getExtension(chainFile));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output, true)))
            {
                try (BufferedLineReader reader = new BufferedLineReader(IOUtil.openFileForReading(chainFile)))
                {
                    String line;
                    boolean skipCurrentChain = false;
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
                                    totalChainIdUpdated++;
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

                            if (sourceBlacklist.contains(sourceSeq) || targetBlacklist.contains(targetSeq))
                            {
                                skipCurrentChain = true;
                                totalChainSkipped++;
                                continue;
                            }

                            skipCurrentChain = false;
                            totalPassing++;
                            writer.write(StringUtils.join(chainFields, " "));
                            writer.write('\n');
                        }
                        else
                        {
                            if (!skipCurrentChain)
                            {
                                writer.write(line);
                                writer.write('\n');
                            }
                        }
                    }
                }

                if (totalPassing == 0)
                {
                    throw new IllegalArgumentException("No lines passed in this chain file");
                }

                messages.add("Total passing chains: " + totalPassing);

                if (totalChainSkipped > 0)
                {
                    messages.add("Chains Skipped Due to Unknown Contigs: " + totalChainSkipped);
                }

                if (totalChainIdUpdated > 0)
                {
                    messages.add("Duplicates Chain Ids Updated: " + totalChainIdUpdated);
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

    private final Map<Integer, Map<String, String>> _cachedReferencesByGenome = new HashMap<>();

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

            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("library_id"), genomeId);
            filter.addCondition(FieldKey.fromString("alias"), null, CompareType.NONBLANK);
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), PageFlowUtil.set("ref_nt_id", "alias"), filter, null);
            if (ts.exists())
            {
                ts.forEachResults(new Selector.ForEachBlock<Results>()
               {
                   @Override
                   public void exec(Results rs) throws SQLException
                   {
                       String name = RefNtSequenceModel.getForRowId(rs.getInt(FieldKey.fromString("ref_nt_id"))).getName();
                       String alias = rs.getString(FieldKey.fromString("alias"));
                       cachedReferences.put(alias, name);
                   }
               });
            }

            _cachedReferencesByGenome.put(genomeId, cachedReferences);
        }

        Map<String, String> cachedReferences = _cachedReferencesByGenome.get(genomeId);

        if (cachedReferences.containsKey(refName))
        {
            return cachedReferences.get(refName);
        }

        //UCSC is a main source of chain files, so deal with their quirks:
        // https://genome.ucsc.edu/cgi-bin/hgGateway
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
        else if (refName.endsWith("_fix"))
        {
            refName = refName.replaceAll("_fix$", "");
        }
        else if (refName.endsWith("_alt"))
        {
            refName = refName.replaceAll("_alt$", "");
        }

        if (refName.startsWith("chr") && refName.contains("_"))
        {
            String[] tokens = refName.split("_");
            refName = StringUtils.join(Arrays.copyOfRange(tokens, 1, tokens.length), "_");
        }

        if (refName.equals("chrM"))
        {
            return "MT";
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
            assertEquals("NW_021160383.1", translateUcscUnplaced("chrX_NW_021160383v1_fix"));

            //chrM???
        }
    }
}

