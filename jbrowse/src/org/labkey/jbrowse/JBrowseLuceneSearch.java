package org.labkey.jbrowse;

import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONObject;
import org.labkey.api.security.User;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import static java.lang.Integer.parseInt;

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

    private static class LuceneFieldDescriptor
    {
        private final String _luceneFieldName;
        private final VCFHeaderLineType _type;

        public LuceneFieldDescriptor(String luceneFieldName, VCFHeaderLineType type)
        {
            _luceneFieldName = luceneFieldName;
            _type = type;
        }
    }

    private static final String VARIABLE_SAMPLES = "variableSamples";

    private Map<String, LuceneFieldDescriptor> getIndexedFields()
    {
        Map<String, LuceneFieldDescriptor> ret = new HashMap<>();

        File vcf = _jsonFile.getTrackFile();
        if (!vcf.exists()){
            throw new IllegalArgumentException("Unable to find file: " + vcf.getPath());
        }

        try (VCFFileReader reader = new VCFFileReader(vcf))
        {
            VCFHeader header = reader.getFileHeader();
            for (String fn : _jsonFile.getInfoFieldsToIndex())
            {
                if (VARIABLE_SAMPLES.equals(fn))
                {
                    ret.put(VARIABLE_SAMPLES, new LuceneFieldDescriptor(VARIABLE_SAMPLES, VCFHeaderLineType.Character));
                    continue;
                }

                if (!header.hasInfoLine(fn))
                {
                    throw new IllegalArgumentException("Field not present: " + fn);
                }

                VCFInfoHeaderLine info = header.getInfoHeaderLine(fn);
                ret.put(fn, new LuceneFieldDescriptor(fn, info.getType()));
            }
        }

        return ret;
    }

    public JSONObject doSearch(final String searchString, final int pageSize, final int offset) throws IOException, ParseException
    {
        File indexPath = _jsonFile.getExpectedLocationOfLuceneIndex(true);
        Map<String, LuceneFieldDescriptor> fieldMap = getIndexedFields();

        // Open directory of lucene path, get a directory reader, and create the index search manager
        try (
                Directory indexDirectory = FSDirectory.open(indexPath.toPath());
                IndexReader indexReader = DirectoryReader.open(indexDirectory);
                Analyzer analyzer = new StandardAnalyzer();
        )
        {
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
            StringTokenizer tokenizer = new StringTokenizer(searchString, "&");
            while (tokenizer.hasMoreTokens())
            {
                String filter = tokenizer.nextToken();
                String[] parts = filter.split(",");
                String field = parts[0];
                String operator = parts[1];
                String valueStr = parts[2];

                Query query = null;

                if (!fieldMap.containsKey(field))
                {

                }
                LuceneFieldDescriptor fieldDescriptor = fieldMap.get(field);

                // TODO: switch based on field.type
                switch (fieldDescriptor._type)
                {
                    case Integer:
                    {
                        int value = parseInt(valueStr);
                        if (operator.equals("="))
                        {
                            query = IntPoint.newExactQuery(field, value);
                        }
                        else if (operator.equals("!="))
                        {
                            query = new BooleanQuery.Builder()
                                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD)
                                    .add(IntPoint.newExactQuery(field, value), BooleanClause.Occur.MUST_NOT)
                                    .build();
                        }
                        else if (operator.equals(">"))
                        {
                            query = IntPoint.newRangeQuery(field, value + 1, Integer.MAX_VALUE);
                        }
                        else if (operator.equals(">="))
                        {
                            query = IntPoint.newRangeQuery(field, value, Integer.MAX_VALUE);
                        }

                        else if (operator.equals("<"))
                        {
                            query = IntPoint.newRangeQuery(field, Integer.MIN_VALUE, value - 1);
                        }
                        else if (operator.equals("<="))
                        {
                            query = IntPoint.newRangeQuery(field, Integer.MIN_VALUE, value);
                        }
                        else if (operator.equals("is not empty"))
                        {
                            query = new TermQuery(new Term(field));
                        }
                        else if (operator.equals("is empty"))
                        {
                            BooleanQuery.Builder builder = new BooleanQuery.Builder();
                            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                            builder.add(new TermQuery(new Term(field)), BooleanClause.Occur.MUST_NOT);
                            query = builder.build();
                        }
                    }
                    break;
                    case Float:
                    {
                        float value = Float.parseFloat(valueStr);
                        if (operator.equals("="))
                        {
                            query = FloatPoint.newExactQuery(field, value);
                        }
                        else if (operator.equals("!="))
                        {
                            query = new BooleanQuery.Builder()
                                    .add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD)
                                    .add(FloatPoint.newExactQuery(field, value), BooleanClause.Occur.MUST_NOT)
                                    .build();
                        }
                        else if (operator.equals(">"))
                        {
                            query = FloatPoint.newRangeQuery(field, value + 1.0f, Float.POSITIVE_INFINITY);
                        }
                        else if (operator.equals(">="))
                        {
                            query = FloatPoint.newRangeQuery(field, value, Float.POSITIVE_INFINITY);
                        }
                        else if (operator.equals("<"))
                        {
                            query = FloatPoint.newRangeQuery(field, Float.NEGATIVE_INFINITY, value - 1.0f);
                        }
                        else if (operator.equals("<="))
                        {
                            query = FloatPoint.newRangeQuery(field, Float.NEGATIVE_INFINITY, value);
                        }
                        else if (operator.equals("is not empty"))
                        {
                            query = new TermQuery(new Term(field));
                        }
                        else if (operator.equals("is empty"))
                        {
                            BooleanQuery.Builder builder = new BooleanQuery.Builder();
                            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                            builder.add(new TermQuery(new Term(field)), BooleanClause.Occur.MUST_NOT);
                            query = builder.build();
                        }
                    }
                    break;
                    case Character:
                    {
                        QueryParser parser = new QueryParser(field, analyzer);
                        parser.setAllowLeadingWildcard(true);

                        if (operator.equals("is"))
                        {
                            query = parser.parse(field + ":" + valueStr);
                        }
                        else if (operator.equals("contains"))
                        {
                            query = parser.parse(field + ":*" + valueStr + "*");
                        }
                        else if (operator.equals("starts with"))
                        {
                            query = parser.parse(field + ":" + valueStr + "*");
                        }
                        else if (operator.equals("ends with"))
                        {
                            query = parser.parse(field + ":*" + valueStr);
                        }
                        else if (operator.equals("is empty"))
                        {
                            query = parser.parse("-" + field + ":[* TO *]");
                        }
                        else if (operator.equals("is not empty"))
                        {
                            query = parser.parse(field + ":[* TO *]");
                        }
                    }
                    break;
                    case Flag:
                        //TODO: do something. how do we do this in DISCVRseq? is this basically boolean?
                    break;

                    // TODO: restore this in some manner. maybe outside this switch statement?
//                    case "Advanced":
//                    {
//                        QueryParser parser = new QueryParser(field, analyzer);
//                        query = parser.parse(valueStr);
//                    }
//                    break;
                }

                booleanQueryBuilder.add(query, BooleanClause.Occur.MUST);
            }

            BooleanQuery query = booleanQueryBuilder.build();

            // Get chunks of size {pageSize}. Default to 1 chunk -- add to the offset to get more.
            // We then iterate over the range of documents we want based on the offset. This does grow in memory
            // linearly with the number of documents, but my understanding is that these are just score,id pairs
            // rather than full documents, so mem usage *should* still be pretty low.
            TopDocs topDocs = indexSearcher.search(query, pageSize * (offset + 1));

            JSONObject results = new JSONObject();

            // Iterate over the doc list, (either to the total end or until the page ends) grab the requested docs,
            // and add to returned results
            List<JSONObject> data = new ArrayList<>();
            for (int i = pageSize * offset; i < Math.min(pageSize * (offset + 1), topDocs.scoreDocs.length); i++)
            {
                JSONObject elem = new JSONObject();

                indexSearcher.doc(topDocs.scoreDocs[i].doc).forEach(field -> {
                    elem.put(field.name(), field.stringValue());
                });

                data.add(elem);
            }

            results.put("data", data);
            indexReader.close();

            //TODO: we should probably stream this
            return results;
        }
    }
}
