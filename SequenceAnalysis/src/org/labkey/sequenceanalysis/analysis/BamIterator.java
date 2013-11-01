/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.analysis;

import net.sf.picard.reference.FastaSequenceIndex;
import net.sf.picard.reference.IndexedFastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/1/12
 * Time: 12:33 PM
 */
public class BamIterator
{
    private File _bam;
    private File _bai;
    private File _ref;
    private IndexedFastaSequenceFile _refFastq;

    private Logger _logger;
    private Map<String, ReferenceSequence> _references = new HashMap<>();

    public static final byte INDEL_CHARACTER = (byte)'-';
    public static final byte AMBIGUITY_CHARACTER = (byte)'N';

    private List<AlignmentAggregator> _alignmentAggregators = new ArrayList<>();

    public BamIterator(File bam, File refFasta, Logger logger) throws FileNotFoundException
    {
        _bam = bam;
        _ref = refFasta;
        _logger = logger;

        _bai = new File(_bam.getPath() + ".bai");
        if(!_bai.exists())
            throw new FileNotFoundException("Missing index for BAM, expected: " + _bai.getPath());

        File fai = new File(_ref.getPath() + ".fai");
        if(!fai.exists())
            throw new FileNotFoundException("Missing index for FASTA, expected: " + fai.getPath());

        _refFastq = new IndexedFastaSequenceFile(_ref, new FastaSequenceIndex(fai));
    }

    public void addAggregators(List<AlignmentAggregator> aggregators)
    {
        _alignmentAggregators.addAll(aggregators);
    }

    public void addAggregator(AlignmentAggregator aggregator)
    {
        _alignmentAggregators.add(aggregator);
    }

    public void iterateReads(String refName, int start, int stop)
    {
        SAMFileReader sam = null;

        try
        {
            sam = new SAMFileReader(_bam, _bai);
            sam.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);

            //TODO: verify whether this is useful
            for (int i = 0; i < sam.getFileHeader().getSequenceDictionary().getSequences().size(); i++)
            {
                _logger.info("BAM summary:");

                int count = sam.getIndex().getMetaData(i).getAlignedRecordCount();
                _logger.info("\tAligned read count: " + count);
                int unaligned = sam.getIndex().getMetaData(i).getUnalignedRecordCount();
                _logger.info("\tUnaligned read count: " + unaligned);
            }

            SAMRecordIterator it = sam.iterator();
            int i = 0;
            long startTime = new Date().getTime();

            while (it.hasNext())
            {
                i++;

                SAMRecord r = it.next();
                if (r.getReadUnmappedFlag() || !refName.equals(r.getReferenceName()))
                    continue;

                if (r.getAlignmentEnd() < start || r.getAlignmentStart() > stop)
                    continue;

                processRead(r);

                if (i % 10000 == 0)
                {
                    long newTime = new Date().getTime();
                    _logger.info("processed " + i + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                    startTime = newTime;
                }
            }
        }
        finally
        {
            if (sam != null)
                sam.close();
        }
    }

    /**
     * Iterates all reads in the alignment
     * @return
     */
    public void iterateReads()
    {
        SAMFileReader sam = null;

        try
        {
            sam = new SAMFileReader(_bam, _bai);
            sam.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);

            SAMRecordIterator it = sam.iterator();
            int i = 0;
            long startTime = new Date().getTime();

            while (it.hasNext())
            {
                i++;

                processRead(it.next());

                if (i % 25000 == 0)
                {
                    long newTime = new Date().getTime();
                    _logger.info("processed " + i + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                    startTime = newTime;
                }
            }
        }
        finally
        {
            if (sam != null)
                sam.close();
        }
    }

    private void processRead(SAMRecord r)
    {
        if(r.getReadUnmappedFlag())
        {
            for (AlignmentAggregator aggregator : _alignmentAggregators)
            {
                aggregator.inspectAlignment(r, null, new HashMap<Integer, List<NTSnp>>(), null);
            }
            return;
        }
        assert !(r.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        ReferenceSequence ref = getReferenceSequenceFromFasta(r.getReferenceName());

        Map<Integer, List<NTSnp>> snpPositions = new TreeMap<>();
        CigarPositionIterable cpi = new CigarPositionIterable(r);
        CigarPositionIterable.CigarIterator ci = cpi.iterator();
        while (ci.hasNext())
        {
            CigarPositionIterable.PositionInfo pi = ci.next();
            List<NTSnp> snps = snpPositions.get(pi.getLastRefPosition());
            if (snps == null)
                snps = new ArrayList<>();

            if (pi.includeInSnpCount())
            {
                NTSnp ntSnp = new NTSnp(pi);

                // test whether refBase equals the readBase
                if (ntSnp.getReferenceBase(ref.getBases()) != ntSnp.getReadBase())
                {
                    snps.add(ntSnp);
                }
            }

            if (snps.size() > 0)
                snpPositions.put(pi.getLastRefPosition(), snps);
        }

        //sort SNPs on insert idx
        for (Integer pos : snpPositions.keySet())
        {
            List<NTSnp> sortedSnps = snpPositions.get(pos);
            Collections.sort(sortedSnps, new Comparator<NTSnp>(){
                @Override
                public int compare(NTSnp snp1, NTSnp snp2)
                {
                    Integer pos1 = snp1.getLastRefPosition();
                    Integer idx1 = snp1.getInsertIndex();
                    Integer compare1 = pos1.compareTo(snp2.getLastRefPosition());

                    return compare1 != 0 ? compare1 : idx1.compareTo(snp2.getInsertIndex());
                }
            });
            snpPositions.put(pos, sortedSnps);
        }

        for (AlignmentAggregator aggregator : _alignmentAggregators)
        {
            aggregator.inspectAlignment(r, ref, snpPositions, cpi);
        }
    }

    private ReferenceSequence getReferenceSequenceFromFasta(String refName)
    {
        if (_references.containsKey(refName))
        {
            return _references.get(refName);
        }
        else
        {
            _references.put(refName, _refFastq.getSequence(refName));
            return _references.get(refName);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "BAMIteratorTestProject";
        private final Logger _log = Logger.getLogger(TestCase.class);
        private Container _project;
        private File _pipelineRoot;
        private File _sampleData;
        private File _bam;
        private File _refFasta;

        @BeforeClass
        public static void before() throws Exception
        {
            //pre-clean
            cleanup();

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                if (project == null)
                {
                    project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                    Set<Module> modules = new HashSet<>();
                    modules.addAll(project.getActiveModules());
                    modules.add(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
                    project.setFolderType(ModuleLoader.getInstance().getFolderType("Laboratory Folder"), TestContext.get().getUser());
                    project.setActiveModules(modules);
                }
            }
        }

        @Before
        public void setUp() throws Exception
        {
            _project = ContainerManager.getForPath(PROJECT_NAME);

            _sampleData = getSampleDataDir();
            if (_sampleData == null || !_sampleData.exists())
            {
                throw new Exception("sampledata folder does not exist: " + _sampleData.getPath());
            }

            _pipelineRoot = PipelineService.get().getPipelineRootSetting(_project).getRootPath();

            _bam = getBAM();
            _refFasta = getRefFasta();
        }

        @Test
        public void basicTest() throws Exception
        {
            Map<String, String> params = new HashMap<>();
            params.put("snp.minAvgSnpQual", "17");
            params.put("snp.minSnpQual", "17");
            params.put("snp.minAvgDipQual", "17");
            params.put("snp.minDipQual", "17");
            SequencePipelineSettings settings = new SequencePipelineSettings(params);

            AvgBaseQualityAggregator agg1 = new AvgBaseQualityAggregator(_bam, _refFasta, _log);
            Map<Integer, Map<String, Double>> quals = agg1.getQualsForReference(0);
            //TODO: test expectations
            Assert.assertNotNull(quals);
            Assert.assertEquals(8617, quals.size());

//            for (Map<String, Double> map : quals.get(0).values())
//            {
//
//            }

            Mockery mock = new Mockery();
            mock.setImposteriser(ClassImposteriser.INSTANCE);

            final Logger log2 = mock.mock(Logger.class, "log2");
            mock.checking(new Expectations() {{
                oneOf(log2).info("Saving Coverage Results to DB");
                oneOf(log2).info("\tReference sequences saved: 1");
                oneOf(log2).info("\tPositions saved by reference (may include indels, so total could exceed reference length):");
                oneOf(log2).info("\t1|SIVmac239: 8892");
            }});

            final Logger log3 = mock.mock(Logger.class, "log3");
            mock.checking(new Expectations() {{
                oneOf(log3).info("Saving NT SNP results to DB");
                oneOf(log3).info("\tReference sequences saved: 1");
                oneOf(log3).info("\tSNPs saved by reference:");
                oneOf(log3).info("\tSIVmac239: 473");
            }});

            final Logger log4 = mock.mock(Logger.class, "log4");
            mock.checking(new Expectations() {{
                oneOf(log4).info("Saving AA SNP Results to DB");
                oneOf(log4).info("\tTotal AA Reference sequences encountered: 11");
                oneOf(log4).info("\tSNPs saved by reference:");
                oneOf(log4).info("\t1|SIVmac239 Nef: 9");
                oneOf(log4).info("\t1|SIVmac239 Tat: 22");
                oneOf(log4).info("\t1|SIVmac239 Env ARF 10: 10");
                oneOf(log4).info("\t1|SIVmac239 vpX: 14");
                oneOf(log4).info("\t1|SIVmac239 Env: 50");
                oneOf(log4).info("\t1|SIVmac239 Rev: 9");
                oneOf(log4).info("\t1|SIVmac239 Vif: 20");
                oneOf(log4).info("\t1|SIVmac239 Gag: 23");
                oneOf(log4).info("\t1|SIVmac239 Env ARF 1: 6");
                oneOf(log4).info("\t1|SIVmac239 vpR: 10");
                oneOf(log4).info("\t1|SIVmac239 Pol: 86");
            }});

            NtCoverageAggregator agg2 = new NtCoverageAggregator(settings, log2, agg1);
            agg2.setLogProgress(false);
            NtSnpByPosAggregator agg3 = new NtSnpByPosAggregator(settings, log3, agg1);
            agg3.setLogProgress(false);
            AASnpByCodonAggregator agg4 = new AASnpByCodonAggregator(settings, log4, agg1);
            agg4.setLogProgress(false);
            AASnpByReadAggregator agg5 = new AASnpByReadAggregator(settings, _log, agg1);
            agg5.setLogProgress(false);

            BamIterator bi = new BamIterator(_bam, _refFasta, _log);
            bi.addAggregators(Arrays.asList((AlignmentAggregator)agg2, agg3, agg4, agg5));
            bi.iterateReads();

            //TODO: should create methods that expose output w/o needing DB
            AnalysisModel m = new AnalysisModel();
            m.setContainer(_project.getId());
            Table.insert(TestContext.get().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES), m);

            agg2.saveToDb(TestContext.get().getUser(), _project, m);
            TableSelector coverage = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_COVERAGE), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long coverageCount = coverage.getRowCount();
            Assert.assertEquals("Incorrect coverage count", 8892L, coverageCount);

            agg3.saveToDb(TestContext.get().getUser(), _project, m);
            TableSelector ntPos = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long ntPosCount = ntPos.getRowCount();
            Assert.assertEquals("Incorrect NT pos count", 473L, ntPosCount);

            agg4.saveToDb(TestContext.get().getUser(), _project, m);
            TableSelector aaByCodon = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long aaByCodonCount = aaByCodon.getRowCount();
            Assert.assertEquals("Incorrect AA codon count", 259L, aaByCodonCount);

            List<Map<String, Object>> results = agg5.getResults(TestContext.get().getUser(), _project, m);


            //TODO: write test

        }

        private static File getSampleDataDir()
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse("sampledata"));
            File file = null;
            for (Resource r : resource.list())
            {
                if(r instanceof FileResource)
                {
                    file = ((FileResource) r).getFile().getParentFile();
                    break;
                }
            }
            return file;
        }

        private File getBAM() throws FileNotFoundException
        {
            File file = new File(getSampleDataDir(), "test.bam");
            if (!file.exists())
                throw new FileNotFoundException("File not found: " + file.getPath());

            return file;
        }

        private File getRefFasta() throws FileNotFoundException
        {
            File file = new File(getSampleDataDir(), "Ref_DB.fasta");
            if (!file.exists())
                throw new FileNotFoundException("File not found: " + file.getPath());

            return file;
        }

        @AfterClass
        public static void cleanup()
        {
            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project != null)
            {
                File _pipelineRoot = PipelineService.get().getPipelineRootSetting(project).getRootPath();
                try
                {
                    if (_pipelineRoot.exists())
                    {
                        for (File f : _pipelineRoot.listFiles())
                        {
                            if (f.exists())
                            {
                                if (f.isDirectory())
                                    FileUtils.deleteDirectory(f);
                                else
                                    f.delete();
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                ContainerManager.delete(project, TestContext.get().getUser());
            }
        }
    }

    public void saveSynopsis (User u, AnalysisModel model) throws PipelineJobException
    {
        StringBuilder synopsis = new StringBuilder();
        for (AlignmentAggregator a : _alignmentAggregators)
        {
            synopsis.append(a.getSynopsis());
        }

        try
        {
            model.setSynopsis(synopsis.toString());
            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
            Table.update(u, ti, model, model.getRowId());
        }
        catch (SQLException e)
        {
            throw  new PipelineJobException(e);
        }
    }
}