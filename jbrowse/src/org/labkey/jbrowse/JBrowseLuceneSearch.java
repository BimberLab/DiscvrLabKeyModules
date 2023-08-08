package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.jbrowse.AbstractJBrowseFieldCustomizer;
import org.labkey.api.jbrowse.GroupsProvider;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
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
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.jbrowse.JBrowseFieldUtils.VARIABLE_SAMPLES;
import static org.labkey.jbrowse.JBrowseFieldUtils.getSession;
import static org.labkey.jbrowse.JBrowseFieldUtils.getTrack;

public class JBrowseLuceneSearch
{
    private final JBrowseSession _session;
    private final JsonFile _jsonFile;
    private final User _user;

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

    private String templateReplace(final String searchString) {
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

    private String tryUrlDecode(String input) {
        try {
            //special case for urls containing +; this isn't necessary for strings sent from the client-side, but URLs
            //sent via unit tests autodecode, and strings containing + rather than the URL-encoded symbol are unsafe
            //to pass through URLDecoded.decode
            if(input.contains("+")) {
                return input;
            }

            return URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return input;
        }
    }


    public JSONObject doSearch(User u, String searchString, final int pageSize, final int offset) throws IOException, ParseException
    {
        searchString = tryUrlDecode(searchString);
        File indexPath = _jsonFile.getExpectedLocationOfLuceneIndex(true);
        Map<String, JBrowseFieldDescriptor> fields = JBrowseFieldUtils.getIndexedFields(_jsonFile, u, getContainer());

        // Open directory of lucene path, get a directory reader, and create the index search manager
        try (
                Directory indexDirectory = FSDirectory.open(indexPath.toPath());
                IndexReader indexReader = DirectoryReader.open(indexDirectory);
                Analyzer analyzer = new StandardAnalyzer();
        )
        {
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);

            List<String> stringQueryParserFields = new ArrayList<>();
            List<String> numericQueryParserFields = new ArrayList<>();
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
                        numericQueryParserFields.add(field);
                        pointsConfigMap.put(field, doublePointsConfig);
                    }
                    case Integer -> {
                        numericQueryParserFields.add(field);
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

            if (searchString.equals("all")) {
                booleanQueryBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            }

            // Split input into tokens, 1 token per query separated by &
            StringTokenizer tokenizer = new StringTokenizer(searchString, "&");

            while (tokenizer.hasMoreTokens() && !searchString.equals("all"))
            {
                String queryString = tokenizer.nextToken();
                Query query = null;

                // Type is defined by the first field in the lucene query
                // "First" field is defined by getting the first consecutive string of ASCII characters or underscores terminated by a colon
                // we might just want to return the field(s) in the form instead
                Pattern pattern = Pattern.compile("[\\p{ASCII}&&[^\\s:*+-]][\\p{ASCII}&&[^:\\p{Punct}*]]*:");

                Matcher matcher = pattern.matcher(queryString);

                String fieldName = null;
                if (matcher.find())
                {
                    fieldName = matcher.group().substring(0, matcher.group().length() - 1);
                }

                if (VARIABLE_SAMPLES.equals(fieldName))
                {
                    queryString = templateReplace(queryString);
                }

                if (stringQueryParserFields.contains(fieldName))
                {
                    query = queryParser.parse(queryString);
                }
                else if(numericQueryParserFields.contains(fieldName))
                {
                    try
                    {
                        query = numericQueryParser.parse(queryString, "");
                    }
                    catch (QueryNodeException e)
                    {
                        e.printStackTrace();
                    }
                } else
                {
                    throw new IllegalArgumentException("No such field(s), or malformed query.");
                }

                booleanQueryBuilder.add(query, BooleanClause.Occur.MUST);
            }

            BooleanQuery query = booleanQueryBuilder.build();

            // Get chunks of size {pageSize}. Default to 1 chunk -- add to the offset to get more.
            // We then iterate over the range of documents we want based on the offset. This does grow in memory
            // linearly with the number of documents, but my understanding is that these are just score,id pairs
            // rather than full documents, so mem usage *should* still be pretty low.
            //TopDocs topDocs = indexSearcher.search(query, pageSize * (offset + 1));

            // Define sort field
            SortField sortField = new SortField("pos", SortField.Type.INT, false);
            Sort sort = new Sort(sortField);

            // Perform the search with sorting
            TopFieldDocs topDocs = indexSearcher.search(query, pageSize * (offset + 1), sort);

            JSONObject results = new JSONObject();

            // Iterate over the doc list, (either to the total end or until the page ends) grab the requested docs,
            // and add to returned results
            List<JSONObject> data = new ArrayList<>();
            for (int i = pageSize * offset; i < Math.min(pageSize * (offset + 1), topDocs.scoreDocs.length); i++)
            {
                JSONObject elem = new JSONObject();
                Document doc = indexSearcher.doc(topDocs.scoreDocs[i].doc);

                for (IndexableField field : doc.getFields()) {
                    String fieldName = field.name();
                    String[] fieldValues = doc.getValues(fieldName);
                    if (fieldValues.length > 1) {
                        // If there is more than one value, put the array of values into the JSON object.
                        elem.put(fieldName, fieldValues);
                    } else {
                        // If there is only one value, just put this single value into the JSON object.
                        elem.put(fieldName, fieldValues[0]);
                    }
                }

                data.add(elem);
            }

            results.put("data", data);

            //TODO: we should probably stream this
            return results;
        }
    }

    public static class DefaultJBrowseCustomizer extends AbstractJBrowseFieldCustomizer
    {
        public DefaultJBrowseCustomizer()
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
                ret.add("Rare Variants|AF,>,0.05");
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
}
