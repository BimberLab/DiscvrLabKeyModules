package org.labkey.api.jbrowse;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by bimber on 11/3/2016.
 */
abstract public class JBrowseService
{
    static JBrowseService _instance;

    public static JBrowseService get()
    {
        return _instance;
    }

    static public void setInstance(JBrowseService instance)
    {
        _instance = instance;
    }

    abstract public String prepareOutputFile(User u, Logger log, Integer outputFileId, boolean forceRecreateJson, @Nullable JSONObject additionalConfig) throws IOException;

    abstract public void onGenomeChange(Container c, User u, int genomeId, Logger log) throws PipelineJobException;

    abstract public void reprocessDatabase(User u, String databaseGuid) throws PipelineValidationException;

    abstract public void registerDemographicsSource(DemographicsSource source);

    abstract public void registerGroupsProvider(GroupsProvider provider);

    abstract public void registerFieldCustomizer(JBrowseFieldCustomizer customizer);

    abstract public void prepareLuceneIndex(File vcf, File indexDir, Logger log, List<String> infoFieldsForFullTextSearch, boolean allowLenientLuceneProcessing) throws PipelineJobException;

    abstract public SequenceOutputFile findMatchingLuceneIndex(SequenceOutputFile vcfFile, List<String> infoFieldsToIndex, User u, @Nullable Logger log) throws PipelineJobException;

    abstract public void registerLuceneIndexDetector(LuceneIndexDetector detector);

    abstract public void cacheDefaultQuery(User u, String sessionId, String trackId);

    public interface LuceneIndexDetector
    {
        SequenceOutputFile findMatchingLuceneIndex(SequenceOutputFile vcfFile, List<String> infoFieldsToIndex, User u, @Nullable Logger log) throws PipelineJobException;

        boolean isAvailable(Container c);
    }
}
