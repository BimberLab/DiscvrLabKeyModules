package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.IOUtil;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        try (BufferedLineReader reader = new BufferedLineReader(IOUtil.openFileForReading(chainFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.equals(""))
                {
                    continue;
                }
                else if (line.startsWith("#"))
                {
                    hasChanges = true;
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
                        if (resolveSequenceId(sourceSeq, sourceGenome) == null)
                        {
                            throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": unable to resolve sequence with name " + sourceSeq + " in reference genome: " + sourceGenome + ".  There names of the contigs in the chain file must match the names used for the sequences within the site.");
                        }

                        //and targetRefName
                        String targetSeq = chainFields[7];
                        if (resolveSequenceId(sourceSeq, sourceGenome) == null)
                        {
                            throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": unable to resolve sequence with name " + targetSeq + " in reference genome: " + targetGenome + ".  There names of the contigs in the chain file must match the names used for the sequences within the site.");
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
        if (!hasChanges)
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
                        if (line.startsWith("#"))
                        {
                            continue;
                        }

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

                                String newLine = line.replaceAll("(?<=\\s)" + chainFields[12] + "$", String.valueOf(chainId));
                                writer.write(newLine);
                                writer.write('\n');
                            }
                            catch (NumberFormatException e)
                            {
                                throw new IllegalArgumentException("Line " + reader.getLineNumber() + ": chain Id should be an integer: " + chainFields[12]);
                            }

                            //TODO: make sure the sequence names match our DB
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

    private Map<Integer, Map<String, Integer>> _cachedReferencesByGenome = new HashMap<>();

    private Integer resolveSequenceId(String refName, int genomeId)
    {
        if (!_cachedReferencesByGenome.containsKey(genomeId))
        {
            final Map<String, Integer> cachedReferences = new CaseInsensitiveHashMap<>();
            SqlSelector ss = new SqlSelector(DbScope.getLabKeyScope(), new SQLFragment("SELECT r.rowid, r.name FROM sequenceanalysis.ref_nt_sequences r WHERE r.rowid IN (SELECT ref_nt_id FROM sequenceanalysis.reference_library_members m WHERE m.library_id = ?) ", genomeId));
            ss.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    cachedReferences.put(rs.getString("name"), rs.getInt("rowid"));
                }
            });

            _cachedReferencesByGenome.put(genomeId, cachedReferences);
        }

        Map<String, Integer> cachedReferences = _cachedReferencesByGenome.get(genomeId);

        return cachedReferences.get(refName);
    }
}
