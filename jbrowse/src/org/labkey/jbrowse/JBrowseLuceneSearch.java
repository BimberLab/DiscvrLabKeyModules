package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.jbrowse.AbstractJBrowseFieldCustomizer;
import org.labkey.api.jbrowse.GroupsProvider;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Filter;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.jbrowse.JBrowseFieldUtils.VARIABLE_SAMPLES;
import static org.labkey.jbrowse.JBrowseFieldUtils.getSession;
import static org.labkey.jbrowse.JBrowseFieldUtils.getTrack;

public class JBrowseLuceneSearch
{
    private static final Logger _log = LogHelper.getLogger(JBrowseLuceneSearch.class, "Logger related to JBrowse/Lucene indexing and queries");
    private final JBrowseSession _session;
    private final JsonFile _jsonFile;
    private final User _user;
    private final String[] specialStartPatterns = {"*:* -", "+", "-"};
    private static final String ALL_DOCS = "all";
    private static final String GENOMIC_POSITION = "genomicPosition";
    private static final int maxCachedQueries = 1000;
    private static final long maxRamBytesUsed = 250 * 1024 * 1024L;

    private static final Cache<String, CacheEntry> _cache = new LuceneIndexCache();

    private JBrowseLuceneSearch(final JBrowseSession session, final JsonFile jsonFile, User u)
    {
        _session = session;
        _jsonFile = jsonFile;
        _user = u;
    }

    private Container getContainer()
    {
        return ContainerManager.getForId(_session.getContainer());
    }

    public static JBrowseLuceneSearch create(String sessionId, String trackId, User u)
    {
        JBrowseSession session = getSession(sessionId);

        return new JBrowseLuceneSearch(session, getTrack(session, trackId, u), u);
    }

    private static synchronized CacheEntry getCacheEntryForSession(String trackObjectId, File indexPath) throws IOException {
        CacheEntry cacheEntry = _cache.get(trackObjectId);

        // Open directory of lucene path, get a directory reader, and create the index search manager
        if (cacheEntry == null)
        {
            try
            {
                Directory indexDirectory = FSDirectory.open(indexPath.toPath());
                LRUQueryCache queryCache = new LRUQueryCache(maxCachedQueries, maxRamBytesUsed);
                IndexReader indexReader = DirectoryReader.open(indexDirectory);
                IndexSearcher indexSearcher = new IndexSearcher(indexReader);
                indexSearcher.setQueryCache(queryCache);
                indexSearcher.setQueryCachingPolicy(new ForceMatchAllDocsCachingPolicy());
                cacheEntry = new CacheEntry(queryCache, indexSearcher, indexPath);
                _cache.put(trackObjectId, cacheEntry);
            }
            catch (Exception e)
            {
                _log.error("Error creating jbrowse/lucene index reader for: " + trackObjectId, e);

                throw new IllegalStateException("Error creating search index reader for: " + trackObjectId);
            }
        }

        return cacheEntry;
    }

    private String templateReplace(final String searchString)
    {
        String result = searchString;
        Pattern pattern = Pattern.compile("~(.*?)~");
        Matcher matcher = pattern.matcher(searchString);

        while (matcher.find())
        {
            String groupName = matcher.group(1);
            List<String> resolvedGroup = JBrowseServiceImpl.get().resolveGroups(_jsonFile.getJsonTrackId(), groupName, _user, getContainer());
            if (resolvedGroup != null)
            {
                String replacement = "(" + StringUtils.join(resolvedGroup,  " OR ") +")";

                result = result.replace("~" + groupName + "~", replacement);
            }
        }

        return result;
    }

    private String tryUrlDecode(String input)
    {
        try
        {
            //special case for urls containing +; this isn't necessary for strings sent from the client-side, but URLs
            //sent via unit tests autodecode, and strings containing + rather than the URL-encoded symbol are unsafe
            //to pass through URLDecoded.decode
            if (input.contains("+"))
            {
                return input;
            }

            return URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e)
        {
            _log.error("Unable to URL decode input string: " + input, e);

            return input;
        }
    }

    public String extractFieldName(String queryString)
    {
        // Check if the query starts with any of the start patterns
        for (String pattern : specialStartPatterns)
        {
            if (queryString.startsWith(pattern))
            {
                queryString = queryString.substring(pattern.length()).trim();
                break;
            }
        }

        // Split the remaining string by ':' and return the first part (field name)
        String[] parts = queryString.split(":", 2);
        return parts.length > 0 ? parts[0].trim() : null;
    }

    public JSONObject doSearch(User u, String searchString, final int pageSize, final int offset, String sortField, boolean sortReverse) throws IOException, ParseException
    {
        searchString = tryUrlDecode(searchString);
        File indexPath = _jsonFile.getExpectedLocationOfLuceneIndex(true);
        Map<String, JBrowseFieldDescriptor> fields = JBrowseFieldUtils.getIndexedFields(_jsonFile, u, getContainer());

        CacheEntry cacheEntry = getCacheEntryForSession(_jsonFile.getObjectId(), indexPath);
        Analyzer analyzer = new StandardAnalyzer();

        List<String> stringQueryParserFields = new ArrayList<>();
        Map<String, SortField.Type> numericQueryParserFields = new HashMap<>();
        PointsConfig intPointsConfig = new PointsConfig(new DecimalFormat(), Integer.class);
        PointsConfig doublePointsConfig = new PointsConfig(new DecimalFormat(), Double.class);
        Map<String, PointsConfig> pointsConfigMap = new HashMap<>();

        // Iterate fields and split them into fields for the queryParser and the numericQueryParser
        for (Map.Entry<String, JBrowseFieldDescriptor> entry : fields.entrySet())
        {
            String field = entry.getKey();
            JBrowseFieldDescriptor descriptor = entry.getValue();

            switch(descriptor.getType())
            {
                case Flag, String, Character -> stringQueryParserFields.add(field);
                case Float -> {
                    numericQueryParserFields.put(field, SortField.Type.DOUBLE);
                    pointsConfigMap.put(field, doublePointsConfig);
                }
                case Integer -> {
                    numericQueryParserFields.put(field, SortField.Type.LONG);
                    pointsConfigMap.put(field, intPointsConfig);
                }
            }
        }

        // The numericQueryParser can perform range queries, but numeric fields they can't be indexed alongside
        // lexicographic  fields, so they get split into a separate parser
        MultiFieldQueryParser queryParser = new MultiFieldQueryParser(stringQueryParserFields.toArray(new String[0]), new StandardAnalyzer());
        queryParser.setAllowLeadingWildcard(true);

        StandardQueryParser numericQueryParser = new StandardQueryParser();
        numericQueryParser.setAnalyzer(analyzer);
        numericQueryParser.setPointsConfigMap(pointsConfigMap);

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

        if (searchString.equals(ALL_DOCS))
        {
            booleanQueryBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        }

        // Split input into tokens, 1 token per query separated by &
        StringTokenizer tokenizer = new StringTokenizer(searchString, "&");

        while (tokenizer.hasMoreTokens() && !searchString.equals(ALL_DOCS))
        {
            String queryString = tokenizer.nextToken();
            Query query = null;

            String fieldName = extractFieldName(queryString);

            if (VARIABLE_SAMPLES.equals(fieldName))
            {
                queryString = templateReplace(queryString);
            }

            if (stringQueryParserFields.contains(fieldName))
            {
                query = queryParser.parse(queryString);
            }
            else if (numericQueryParserFields.containsKey(fieldName))
            {
                try
                {
                    query = numericQueryParser.parse(queryString, "");
                }
                catch (QueryNodeException e)
                {
                    _log.error("Unable to parse query for field " + fieldName + ": " + queryString, e);

                    throw new IllegalArgumentException("Unable to parse query: " + queryString + " for field: " + fieldName);
                }
            }
            else
            {
                _log.error("No such field(s), or malformed query: " + queryString + ", field: " + fieldName);

                throw new IllegalArgumentException("No such field(s), or malformed query: " + queryString + ", field: " + fieldName);
            }

            booleanQueryBuilder.add(query, BooleanClause.Occur.MUST);
        }

        BooleanQuery query = booleanQueryBuilder.build();

        // By default, sort in INDEXORDER, which is by genomicPosition
        Sort sort = Sort.INDEXORDER;

        // If the sort field is not genomicPosition, use the provided sorting data
        if (!sortField.equals(GENOMIC_POSITION)) {
            SortField.Type fieldType;

            if (stringQueryParserFields.contains(sortField)) {
                fieldType = SortField.Type.STRING;
            } else if (numericQueryParserFields.containsKey(sortField)) {
                fieldType = numericQueryParserFields.get(sortField);
            } else {
                throw new IllegalArgumentException("Could not find type for sort field: " + sortField);
            }

            sort = new Sort(new SortField(sortField + "_sort", fieldType, sortReverse));
        }

        // Get chunks of size {pageSize}. Default to 1 chunk -- add to the offset to get more.
        // We then iterate over the range of documents we want based on the offset. This does grow in memory
        // linearly with the number of documents, but my understanding is that these are just score,id pairs
        // rather than full documents, so mem usage *should* still be pretty low.
        // Perform the search with sorting
        TopFieldDocs topDocs = cacheEntry.indexSearcher.search(query, pageSize * (offset + 1), sort);

        JSONObject results = new JSONObject();

        // Iterate over the doc list, (either to the total end or until the page ends) grab the requested docs,
        // and add to returned results
        List<JSONObject> data = new ArrayList<>();
        for (int i = pageSize * offset; i < Math.min(pageSize * (offset + 1), topDocs.scoreDocs.length); i++)
        {
            JSONObject elem = new JSONObject();
            Document doc = cacheEntry.indexSearcher.storedFields().document(topDocs.scoreDocs[i].doc);

            for (IndexableField field : doc.getFields()) {
                String fieldName = field.name();
                String[] fieldValues = doc.getValues(fieldName);
                if (fieldValues.length > 1) {
                    elem.put(fieldName, fieldValues);
                } else {
                    elem.put(fieldName, fieldValues[0]);
                }
            }

            data.add(elem);
        }

        results.put("data", data);
        results.put("totalHits", topDocs.totalHits.value);

        //TODO: we should probably stream this
        return results;
    }

    public static class DefaultJBrowseFieldCustomizer extends AbstractJBrowseFieldCustomizer
    {
        public DefaultJBrowseFieldCustomizer()
        {
            super(ModuleLoader.getInstance().getModule(JBrowseModule.class));
        }

        @Override
        public void customizeField(JBrowseFieldDescriptor field, Container c, User u)
        {
            switch (field.getFieldName())
            {
                case "AF" -> field.label("Allele Frequency").inDefaultColumns(true);
                case "AC" -> field.label("Allele Count");
                case "MAF" -> field.label("Minor Allele Frequency");
                case "IMPACT" -> field.label("Impact on Protein Coding").allowableValues(Arrays.asList("HIGH", "LOW", "MODERATE", "MODIFIER")).inDefaultColumns(true);
            }
        }

        @Override
        public List<String> getPromotedFilters(Collection<String> indexedFields, Container c, User u)
        {
            List<String> ret = new ArrayList<>();
            if (indexedFields.contains("AF"))
            {
                ret.add("Rare Variants|AF,<,0.05");
            }

            return ret;
        }
    }

    public static class TestJBrowseGroupProvider implements GroupsProvider
    {
        public static final String GROUP_NAME = "!TestGroup!";

        @Override
        public @Nullable List<String> getGroupNames(Container c, User u)
        {
            return AppProps.getInstance().isDevMode() ? Collections.singletonList(GROUP_NAME) : null;
        }

        @Override
        public @Nullable List<String> getGroupMembers(String trackId, String groupName, Container c, User u)
        {
            if (GROUP_NAME.equals(groupName))
            {
                return Arrays.asList("m07952", "m07528", "m07431");
            }

            return null;
        }

        @Override
        public boolean isAvailable(Container c, User u)
        {
            return true;
        }
    }

    public static class ForceMatchAllDocsCachingPolicy implements QueryCachingPolicy {
        private final UsageTrackingQueryCachingPolicy defaultPolicy = new UsageTrackingQueryCachingPolicy();

        @Override
        public boolean shouldCache(Query query) throws IOException {
            if (query instanceof BooleanQuery bq) {
                for (BooleanClause clause : bq) {
                    if (clause.getQuery() instanceof MatchAllDocsQuery) {
                        return true;
                    }
                }
            } else if (query instanceof MatchAllDocsQuery) {
                return true;
            }

            return defaultPolicy.shouldCache(query);
        }

        @Override
        public void onUse(Query query) {
            defaultPolicy.onUse(query);
        }
    }

    public static class LuceneIndexCache implements Cache<String, CacheEntry>
    {
        private final Cache<String, CacheEntry> _cache;

        public LuceneIndexCache()
        {
            _cache = CacheManager.getStringKeyCache(1000, CacheManager.UNLIMITED, "JBrowseLuceneSearchCache");

        }

        @Override
        public void remove(@NotNull String key)
        {
            CacheEntry e = get(key);
            _cache.remove(key);

            closeReader(e);
        }

        @Override
        public void clear()
        {
            for (String key : getKeys())
            {
                closeReader(get(key));
            }

            _cache.clear();
        }

        @Override
        public void close()
        {
            for (String key : getKeys())
            {
                closeReader(get(key));
            }

            _cache.close();
        }

        private void closeReader(@Nullable CacheEntry entry)
        {
            if (entry == null)
            {
                return;
            }

            try
            {
                entry.getIndexSearcher().getIndexReader().close();
            }
            catch (IOException e)
            {
                _log.error("Error closing JBrowseLuceneSearch index reader", e);
            }
        }

        @Override
        public void put(@NotNull String key, CacheEntry value)
        {
            _cache.put(key, value);
        }

        @Override
        public void put(@NotNull String key, CacheEntry value, long timeToLive)
        {
            _cache.put(key, value, timeToLive);
        }

        @Override
        public CacheEntry get(@NotNull String key)
        {
            return _cache.get(key);
        }

        @Override
        public CacheEntry get(@NotNull String key, @Nullable Object arg, CacheLoader<String, CacheEntry> loader)
        {
            return _cache.get(key, arg, loader);
        }

        @Override
        public int removeUsingFilter(Filter<String> filter)
        {
            return _cache.removeUsingFilter(filter);
        }

        @Override
        public Set<String> getKeys()
        {
            return _cache.getKeys();
        }

        @Override
        public TrackingCache<String, CacheEntry> getTrackingCache()
        {
            return _cache.getTrackingCache();
        }

        @Override
        public Cache<String, CacheEntry> createTemporaryCache()
        {
            return _cache.createTemporaryCache();
        }
    }

    public static class CacheEntry
    {
        private final LRUQueryCache queryCache;
        private final IndexSearcher indexSearcher;
        private final File luceneIndexDir;

        public CacheEntry(LRUQueryCache queryCache, IndexSearcher indexSearcher, File luceneIndexDir)
        {
            this.queryCache = queryCache;
            this.indexSearcher = indexSearcher;
            this.luceneIndexDir = luceneIndexDir;
        }

        public LRUQueryCache getQueryCache()
        {
            return queryCache;
        }

        public IndexSearcher getIndexSearcher()
        {
            return indexSearcher;
        }

        public File getLuceneIndexDir()
        {
            return luceneIndexDir;
        }
    }

    public static JSONArray reportCacheInfo()
    {
        JSONArray cacheInfo = new JSONArray();
        for (String sessionId : _cache.getKeys())
        {
            LRUQueryCache qc = _cache.get(sessionId).getQueryCache();
            JSONObject info = new JSONObject();
            info.put("cacheSize", qc.getCacheSize());
            info.put("cacheCount", qc.getCacheCount());
            info.put("hitCount", qc.getHitCount());
            info.put("missCount", qc.getMissCount());
            info.put("evictionCount", qc.getEvictionCount());
            info.put("totalCount", qc.getTotalCount());
            cacheInfo.put(info);
        }

        return cacheInfo;
    }

    public void cacheDefaultQuery()
    {
        try
        {
            JBrowseLuceneSearch.clearCache(_jsonFile.getObjectId());
            doSearch(_user, ALL_DOCS, 100, 0, GENOMIC_POSITION, false);
        }
        catch (ParseException | IOException e)
        {
            _log.error("Unable to cache default query for: " + _jsonFile.getObjectId(), e);
        }
    }

    public static void emptyCache()
    {
        clearCache(null);
    }

    public static void clearCacheForFile(@NotNull File luceneIndexDir)
    {
        for (String key : _cache.getKeys())
        {
            CacheEntry entry = _cache.get(key);
            if (entry != null && luceneIndexDir.equals(entry.getLuceneIndexDir()))
            {
                _cache.remove(key);
            }
        }
    }

    public static void clearCache(@Nullable String jbrowseTrackId)
    {
        if (jbrowseTrackId == null)
        {
            _cache.clear();
        }
        else
        {
            _cache.remove(jbrowseTrackId);
        }
    }

    public static class ShutdownHandler implements ShutdownListener
    {
        @Override
        public String getName()
        {
            return "JBrowse-Lucene Shutdown Listener";
        }

        @Override
        public void shutdownPre()
        {

        }

        @Override
        public void shutdownStarted()
        {
            _log.info("Clearing all open JBrowse/Lucene cached readers");
            JBrowseLuceneSearch.emptyCache();
        }
    }
}
