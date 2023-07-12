package org.labkey.jbrowse;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.security.User;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.api.jbrowse.AbstractJBrowseFieldCustomizer;
import org.labkey.api.jbrowse.JBrowseFieldCustomizer;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.module.ModuleLoader;
import org.labkey.jbrowse.model.JsonFile;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JBrowseLuceneSearch
{
    private final JBrowseSession _session;
    private final JsonFile _jsonFile;

    private JBrowseLuceneSearch(final JBrowseSession session, final JsonFile jsonFile)
    {
        _session = session;
        _jsonFile = jsonFile;
    }

    public static JBrowseLuceneSearch create(String sessionId, String trackId, User u)
    {
        JBrowseSession session = JBrowseSession.getForId(sessionId);
        if (session == null)
        {
            throw new IllegalArgumentException("Unable to find JBrowse session: " + sessionId);
        }

        JsonFile track = session.getTrack(u, trackId);
        if (track == null)
        {
            throw new IllegalArgumentException("Unable to find track with ID: " + trackId);
        }

        if (!track.shouldHaveFreeTextSearch())
        {
            throw new IllegalArgumentException("This track does not support free text search: " + trackId);
        }

        if (!track.doExpectedSearchIndexesExist())
        {
            throw new IllegalArgumentException("The lucene index has not been created for this track: " + trackId);
        }

        return new JBrowseLuceneSearch(session, track);
    }

    private static final String VARIABLE_SAMPLES = "variableSamples";

    private Map<String, JBrowseFieldDescriptor> getIndexedFields()
    {
        Map<String, JBrowseFieldDescriptor> ret = new HashMap<>() {{
            put("contig", new JBrowseFieldDescriptor("contig", "This is the chromosome/contig", true, true, VCFHeaderLineType.String, 1).label("Chromosome"));
            put("ref", new JBrowseFieldDescriptor("ref", "The reference allele", true, true, VCFHeaderLineType.String, 3).label("Ref Allele"));
            put("alt", new JBrowseFieldDescriptor("alt", "The alternate allele", true, true, VCFHeaderLineType.String, 5).label("Alt Allele"));
            put("start", new JBrowseFieldDescriptor("start", "The start position of this variant", true, true, VCFHeaderLineType.Integer, 2).label("Start"));
            put("end", new JBrowseFieldDescriptor("end", "The end position of this variant", true, true, VCFHeaderLineType.Integer, 4).label("End"));
            put("genomicPosition", new JBrowseFieldDescriptor("genomicPosition", "", false, true, VCFHeaderLineType.Integer, 6).hidden(true).label("Genomic Position"));
            put("variableSamples", new JBrowseFieldDescriptor(VARIABLE_SAMPLES, "All samples with this variant", true, true, VCFHeaderLineType.Character, 7).multiValued(true).label("Samples With Variant"));
        }};

        File vcf = _jsonFile.getTrackFile();
        if (!vcf.exists()){
            throw new IllegalArgumentException("Unable to find file: " + vcf.getPath());
        }

        try (VCFFileReader reader = new VCFFileReader(vcf))
        {
            VCFHeader header = reader.getFileHeader();
            for (String fn : _jsonFile.getInfoFieldsToIndex())
            {
                if (!header.hasInfoLine(fn))
                {
                    throw new IllegalArgumentException("Field not present: " + fn);
                }

                VCFInfoHeaderLine info = header.getInfoHeaderLine(fn);
                ret.put(fn, new JBrowseFieldDescriptor(fn, info.getDescription(), false, true, info.getType(), 8));
            }
        }

        return ret;
    }

    public JSONObject returnIndexedFields() {
        Map<String, JBrowseFieldDescriptor> fields = getIndexedFields();
        JSONObject results = new JSONObject();
        JSONArray data = new JSONArray();

        for (Map.Entry<String, JBrowseFieldDescriptor> entry : fields.entrySet()) {
            data.put(entry.getValue().toJSON());
        }

        results.put("fields", data);
        return results;
    }

    private static String templateReplace(final String searchString) {
        Map<String, String> variableMap = new HashMap<>();

        // TODO define and fetch this map
        variableMap.put("ONPRC", "(m07952 OR m07528 OR m07431)");

        String result = searchString;
        Pattern pattern = Pattern.compile("~(.*?)~");
        Matcher matcher = pattern.matcher(searchString);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variableMap.get(variableName);

            if (replacement != null) {
                result = result.replace("~" + variableName + "~", replacement);
            }
        }

        return result;
    }

    private String tryUrlDecode(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return input;
        }
    }


    public JSONObject doSearch(String searchString, final int pageSize, final int offset) throws IOException, ParseException
    {
        searchString = tryUrlDecode(searchString);
        File indexPath = _jsonFile.getExpectedLocationOfLuceneIndex(true);
        Map<String, JBrowseFieldDescriptor> fields = getIndexedFields();

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
            MultiFieldQueryParser queryParser = new MultiFieldQueryParser(stringQueryParserFields.toArray(new String[stringQueryParserFields.size()]), new StandardAnalyzer());
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
                Pattern pattern = Pattern.compile("[\\p{ASCII}&&[^\\s:*-]][\\p{ASCII}&&[^:\\p{Punct}*]]*:");

                Matcher matcher = pattern.matcher(queryString);

                String fieldName = null;
                if (matcher.find()) {
                    fieldName = matcher.group().substring(0, matcher.group().length() - 1);
                }

                if ("variableSamples".equals(fieldName)) {
                    queryString = templateReplace(queryString);
                }

                if (stringQueryParserFields.contains(fieldName))
                {
                    query = queryParser.parse(queryString);
                }
                else if(numericQueryParserFields.contains(fieldName))
                {
                    try {
                        query = numericQueryParser.parse(queryString, "");
                    }
                    catch (QueryNodeException e)
                    {
                        e.printStackTrace();
                    }
                } else {
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
        public void customizeField(JBrowseFieldDescriptor field)
        {
            switch (field.getFieldName())
            {
                case "AF":
                    field.label("Allele Frequency");
                    break;
                case "IMPACT":
                    field.label("Impact on Protein Coding").allowableValues(Arrays.asList("HIGH", "LOW", "MODERATE", "MODIFIER"));
                    break;
            }
        }
    }
}
