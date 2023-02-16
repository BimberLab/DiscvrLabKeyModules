package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import htsjdk.samtools.util.IOUtil;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 2/10/2015.
 */
public class SequenceJobSupportImpl implements SequenceAnalysisJobSupport, Serializable
{
    private final Map<Integer, File> _cachedFilePaths = new HashMap<>();
    private final List<SequenceReadsetImpl> _cachedReadsets = new ArrayList<>();
    private final Map<Integer, AnalysisModel> _cachedAnalyses = new HashMap<>();
    private final Map<Integer, ReferenceGenome> _cachedGenomes = new HashMap<>();
    private final Map<String, Serializable> _cachedObjects = new HashMap<>();

    private transient boolean _modifiedSinceSerialize = false;

    private static final int TEMPORARY_GENOME = -1;

    public SequenceJobSupportImpl()
    {

    }

    @Override
    public SequenceReadsetImpl getCachedReadset(Integer rowId)
    {
        if (rowId == null || rowId < 1)
        {
            throw new IllegalArgumentException("Attempting to access cached readset without a valid ID: " + rowId);
        }

        for (SequenceReadsetImpl rs : _cachedReadsets)
        {
            if (rowId.equals(rs.getRowId()))
            {
                return rs;
            }
        }

        return null;
    }

    @Override
    public AnalysisModel getCachedAnalysis(int rowId)
    {
        return _cachedAnalyses.get(rowId);
    }

    @Override
    public void cacheReadset(int readsetId, User u)
    {
        cacheReadset(readsetId,u, false);
    }

    @Override
    public void cacheReadset(int readsetId, User u, boolean allowReadsetsWithArchivedData)
    {
        SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(readsetId, u);
        if (rs != null)
        {
            cacheReadset(rs, allowReadsetsWithArchivedData);
        }
        else
        {
            throw new IllegalArgumentException("Unable to find readset with rowid: " + readsetId);
        }
    }

    public void cacheReadset(SequenceReadsetImpl m)
    {
        cacheReadset(m, false);
    }

    public void markModified()
    {
        _modifiedSinceSerialize = true;
    }

    public boolean isModifiedSinceSerialize()
    {
        return _modifiedSinceSerialize;
    }

    protected void writeToDisk(File json) throws IOException
    {
        try (OutputStream output = IOUtil.maybeBufferOutputStream(IOUtil.openFileForWriting(json)))
        {
            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            objectMapper.writeValue(output, this);
            _modifiedSinceSerialize = false;
        }
    }

    public void cacheReadset(SequenceReadsetImpl m, boolean allowReadsetsWithArchivedData)
    {
        if (!allowReadsetsWithArchivedData && m.hasArchivedData())
        {
            throw new IllegalArgumentException("Readset has archived data, cannot be used for pipeline jobs", new Exception());
        }

        markModified();
        m.cacheForRemoteServer();
        if (m.existsInDatabase())
        {
            if (getCachedReadset(m.getRowId()) != null)
            {
                _cachedReadsets.remove(getCachedReadset(m.getRowId()));
            }
        }
        else
        {
            _cachedReadsets.remove(m);
        }

        _cachedReadsets.add(m);
    }

    public void cacheAnalysis(AnalysisModelImpl m, PipelineJob job)
    {
        cacheAnalysis(m, job, false);
    }

    public void cacheAnalysis(AnalysisModelImpl m, PipelineJob job, boolean allowReadsetsWithArchivedData)
    {
        markModified();

        if (m.getAlignmentFile() != null)
        {
            cacheExpData(m.getAlignmentData());
        }

        if (m.getReadset() != null)
        {
            SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(m.getReadset(), job.getUser());
            cacheReadset(rs, allowReadsetsWithArchivedData);
        }

        if (m.getLibraryId() != null)
        {
            try
            {
                ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(m.getLibraryId(), job.getUser());
                cacheGenome(rg);
                cacheExpData(ExperimentService.get().getExpData(rg.getFastaExpDataId()));
            }
            catch (PipelineJobException e)
            {
                job.getLogger().error(e.getMessage(), e);
            }
        }

        if (m.getReferenceLibrary() != null)
        {
            cacheExpData(ExperimentService.get().getExpData(m.getReferenceLibrary()));
        }

        _cachedAnalyses.put(m.getRowId(), m);
    }

    @Override
    public void cacheGenome(ReferenceGenome m)
    {
        markModified();

        Integer key = m.getGenomeId();
        if (m.isTemporaryGenome())
        {
            key = TEMPORARY_GENOME;
        }

        if (getCachedGenome(key) != null)
        {
            _cachedGenomes.remove(getCachedGenome(key));
        }

        _cachedGenomes.put(key, m);
    }

    @Override
    public List<Readset> getCachedReadsets()
    {
        return Collections.unmodifiableList(new ArrayList<Readset>(_cachedReadsets));
    }

    @Override
    public List<AnalysisModel> getCachedAnalyses()
    {
        return Collections.unmodifiableList(new ArrayList<>(_cachedAnalyses.values()));
    }

    @Override
    public ReferenceGenome getCachedGenome(int genomeId)
    {
        return _cachedGenomes.get(genomeId);
    }

    @Override
    public Collection<ReferenceGenome> getCachedGenomes()
    {
        return Collections.unmodifiableCollection(_cachedGenomes.values());
    }

    @Override
    public void cacheExpData(ExpData data)
    {
        markModified();

        if (data != null)
        {
            _cachedFilePaths.put(data.getRowId(), data.getFile());
        }
    }

    @Override
    public File getCachedData(int dataId)
    {
        return _cachedFilePaths.get(dataId);
    }

    @Override
    public Map<Integer, File> getAllCachedData()
    {
        return Collections.unmodifiableMap(_cachedFilePaths);
    }

    @Override
    public void cacheObject(String key, Serializable object)
    {
        markModified();

        _cachedObjects.put(key, object);
    }

    @Override
    public <T> T getCachedObject(String key, Class<T> clazz) throws PipelineJobException
    {
        if (_cachedObjects.get(key) != null && clazz.isAssignableFrom(_cachedObjects.get(key).getClass()))
        {
            return clazz.cast(_cachedObjects.get(key));
        }

        ObjectMapper mapper = PipelineJob.createObjectMapper();

        return getCachedObject(key, mapper.getTypeFactory().constructType(clazz));
    }

    @Override
    public <T> T getCachedObject(String key, JavaType type) throws PipelineJobException
    {
        if (_cachedObjects.get(key) == null)
        {
            return null;
        }

        try
        {
            ObjectMapper mapper = PipelineJob.createObjectMapper();

            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, _cachedObjects.get(key));
            return mapper.readValue(new StringReader(writer.toString()), type);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSerializeWithMap() throws Exception
        {
            SequenceJobSupportImpl js1 = new SequenceJobSupportImpl();
            js1._cachedAnalyses.put(1, new AnalysisModelImpl());
            js1._cachedGenomes.put(2, new ReferenceGenomeImpl());
            SequenceReadsetImpl rs1 = new SequenceReadsetImpl();
            rs1.setRowId(100);

            js1._cachedReadsets.add(rs1);
            js1._cachedFilePaths.put(4, new File("/"));

            HashMap<Integer, Integer> map = new HashMap<>();
            map.put(1, 1);
            js1._cachedObjects.put("cachedMap", map);
            js1._cachedObjects.put("cachedInt", 1);
            js1._cachedObjects.put("cachedString", "foo");
            js1._cachedObjects.put("cachedLong", 2L);

            ObjectMapper mapper = PipelineJob.createObjectMapper();

            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, js1);

            SequenceJobSupportImpl deserialized = mapper.readValue(new StringReader(writer.toString()), SequenceJobSupportImpl.class);
            assertNotNull("Analysis map not serialized properly", deserialized._cachedAnalyses.get(1));
            assertNotNull("Genome map not serialized properly", deserialized._cachedGenomes.get(2));
            assertEquals("Readset list not serialized properly", 1, deserialized._cachedReadsets.size());
            assertEquals("Readset not deserialized with correct rowid", 100, deserialized._cachedReadsets.get(0).getRowId());
            assertEquals("Readset not deserialized with correct readsetid", 100, deserialized._cachedReadsets.get(0).getReadsetId().intValue());

            assertNotNull("File map not serialized properly", deserialized._cachedFilePaths.get(4));
            assertNotNull("Cached map not serialized properly", deserialized.getCachedObject("cachedMap",Map.class));
            assertEquals(" Cached int not serialized properly", Integer.valueOf(1), deserialized.getCachedObject("cachedInt", Integer.class));
            assertEquals(" Cached string not serialized properly", "foo", deserialized.getCachedObject("cachedString", String.class));
            assertEquals(" Cached long not serialized properly", 2L, deserialized.getCachedObject("cachedLong", Long.class).longValue());

            //NOTE: this is not serializing properly.  the keys are serialized as Strings
            Map serializedMap = deserialized.getCachedObject("cachedMap", mapper.getTypeFactory().constructParametricType(Map.class, Integer.class, Integer.class));
            assertEquals("Map not serialized properly", 1, serializedMap.size());

            //TODO: determine if we can coax jackson into serializing these properly
            assertEquals("Object not serialized with correct key type", Integer.class, serializedMap.keySet().iterator().next().getClass());
            assertNotNull("Map keys not serialized properly", serializedMap.get(1));
        }

        @Test
        public void testReadsetCaching() {
            SequenceJobSupportImpl js = new SequenceJobSupportImpl();

            SequenceReadsetImpl rs1 = new SequenceReadsetImpl();
            rs1.setRowId(100);
            assertTrue(rs1.existsInDatabase());

            SequenceReadsetImpl rs2 = new SequenceReadsetImpl();
            assertFalse(rs2.existsInDatabase());

            SequenceReadsetImpl rs3 = new SequenceReadsetImpl();
            assertFalse(rs3.existsInDatabase());

            js.cacheReadset(rs1);
            assertEquals(1, js._cachedReadsets.size());

            js.cacheReadset(rs1);
            assertEquals(1, js._cachedReadsets.size());

            js.cacheReadset(rs2);
            assertEquals(2, js._cachedReadsets.size());

            js.cacheReadset(rs3);
            assertEquals(3, js._cachedReadsets.size());

            js.cacheReadset(rs2);
            assertEquals(3, js._cachedReadsets.size());

            js._cachedReadsets.remove(rs2);
            assertEquals(2, js._cachedReadsets.size());

            js.cacheReadset(rs2);
            assertEquals(3, js._cachedReadsets.size());
        }
    }
}
