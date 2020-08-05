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
package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
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
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceIntegrationTests;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: bbimber
 * Date: 7/1/12
 * Time: 12:33 PM
 */
public class BamIterator
{
    private File _bam;
    private File _bai;
    private File _ref;

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
    }

    public void addAggregators(List<AlignmentAggregator> aggregators)
    {
        _alignmentAggregators.addAll(aggregators);
    }

    public void addAggregator(AlignmentAggregator aggregator)
    {
        _alignmentAggregators.add(aggregator);
    }

    public void iterateReads(String refName, int start, int stop) throws IOException, PipelineJobException
    {
        SamReaderFactory bamFact = SamReaderFactory.makeDefault();
        bamFact.validationStringency(ValidationStringency.SILENT);

        File fai = new File(_ref.getPath() + ".fai");
        try (SamReader sam = bamFact.open(_bam);IndexedFastaSequenceFile indexedRef = new IndexedFastaSequenceFile(_ref, new FastaSequenceIndex(fai)))
        {
            try (SAMRecordIterator it = sam.iterator())
            {
                int i = 0;
                long startTime = new Date().getTime();

                while (it.hasNext())
                {
                    i++;

                    SAMRecord r = it.next();
                    if (refName != null && (r.getReadUnmappedFlag() || !refName.equals(r.getReferenceName())))
                        continue;

                    if (r.getAlignmentEnd() < start || r.getAlignmentStart() > stop)
                        continue;

                    processAlignment(r, indexedRef);

                    if (i % 10000 == 0)
                    {
                        long newTime = new Date().getTime();
                        _logger.info("processed " + i + " alignments in " + ((newTime - startTime) / 1000) + " seconds");
                        startTime = newTime;
                    }
                }
            }
        }
    }

    /**
     * Iterates all reads in the alignment
     * @return
     */
    public void iterateReads() throws IOException, PipelineJobException
    {
        iterateReads(null, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private void processAlignment(SAMRecord r, IndexedFastaSequenceFile indexedRef) throws PipelineJobException
    {
        if (r.getReadUnmappedFlag())
        {
            for (AlignmentAggregator aggregator : _alignmentAggregators)
            {
                aggregator.inspectAlignment(r, null, new HashMap<>());
            }
            return;
        }
        assert !(r.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME));

        ReferenceSequence ref = getReferenceSequenceFromFasta(r.getReferenceName(), indexedRef);

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
            sortedSnps.sort((snp1, snp2) ->
            {
                Integer pos1 = snp1.getLastRefPosition();
                Integer idx1 = snp1.getInsertIndex();
                Integer compare1 = pos1.compareTo(snp2.getLastRefPosition());

                return compare1 != 0 ? compare1 : idx1.compareTo(snp2.getInsertIndex());
            });
            snpPositions.put(pos, sortedSnps);
        }

        for (AlignmentAggregator aggregator : _alignmentAggregators)
        {
            aggregator.inspectAlignment(r, ref, snpPositions);
        }
    }

    private ReferenceSequence getReferenceSequenceFromFasta(String refName, IndexedFastaSequenceFile indexedRef)
    {
        if (_references.containsKey(refName))
        {
            return _references.get(refName);
        }
        else
        {
            _references.put(refName, indexedRef.getSequence(refName));
            return _references.get(refName);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "BAMIteratorTestProject";
        private final Logger _log = LogManager.getLogger(TestCase.class);
        private Container _project;
        private File _pipelineRoot;
        private File _sampleData;
        private File _bam;
        private File _refFasta;
        private Integer _refNtId = null;

        @BeforeClass
        public static void before() throws Exception
        {
            //pre-clean
            cleanup();

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                Set<Module> modules = new HashSet<>();
                modules.addAll(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
                project.setFolderType(FolderTypeManager.get().getFolderType("Laboratory Folder"), TestContext.get().getUser());
                project.setActiveModules(modules);
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
            // NOTE: this expects additional records in the DB for amino acids and exons
            // new instances are no longer getting populated automatically.  for now, abort if we dont find the
            // proper records.  ultimately we should have this test provide its own data
            TableInfo ti = QueryService.get().getUserSchema(TestContext.get().getUser(), _project, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES);
            if (!new TableSelector(ti, new SimpleFilter(FieldKey.fromString("ref_nt_id"), _refNtId), null).exists())
            {
                return;
            }

            Map<String, String> params = new HashMap<>();
            params.put("minAvgSnpQual", "17");
            params.put("minSnpQual", "17");
            params.put("minAvgDipQual", "17");
            params.put("minDipQual", "17");

            AvgBaseQualityAggregator agg1 = new AvgBaseQualityAggregator(_log, _bam, _refFasta);
            Map<Integer, Map<String, Double>> quals = agg1.getQualsForReference(0);
            //TODO: test expectations
            Assert.assertNotNull(quals);
            Assert.assertEquals(8617, quals.size());

            Mockery mock = new Mockery();
            mock.setImposteriser(ClassImposteriser.INSTANCE);

            final Logger log2 = mock.mock(Logger.class, "log2");
            mock.checking(new Expectations() {{
                oneOf(log2).info("Saving Coverage Results");
                oneOf(log2).info("\tReference sequences saved: 1");
                oneOf(log2).info("\tPositions saved by reference (may include indels, so total could exceed reference length):");
                oneOf(log2).info("\tSIVmac239_Test: 8892");
            }});

            final Logger log3 = mock.mock(Logger.class, "log3");
            mock.checking(new Expectations() {{
                oneOf(log3).info("Saving NT SNP results");
                oneOf(log3).info("\tReference sequences saved: 1");
                oneOf(log3).info("\tSNPs saved by reference:");
                oneOf(log3).info("\tSIVmac239_Test: 240");
            }});

            final Logger log4 = mock.mock(Logger.class, "log4");
            mock.checking(new Expectations() {{
                oneOf(log4).info("Saving AA SNP Results");
                oneOf(log4).info("\tTotal AA Reference sequences encountered: 11");
                oneOf(log4).info("\tTotal alignments discarded due to low mapping quality: 0");
                oneOf(log4).info("\tSNPs saved by reference:");
                oneOf(log4).info("\tSIVmac239_Test Nef: 9");
                oneOf(log4).info("\tSIVmac239_Test Tat: 22");
                oneOf(log4).info("\tSIVmac239_Test Env ARF 10: 10");
                oneOf(log4).info("\tSIVmac239_Test vpX: 14");
                oneOf(log4).info("\tSIVmac239_Test Env: 50");
                oneOf(log4).info("\tSIVmac239_Test Rev: 9");
                oneOf(log4).info("\tSIVmac239_Test Vif: 20");
                oneOf(log4).info("\tSIVmac239_Test Gag: 23");
                oneOf(log4).info("\tSIVmac239_Test Env ARF 1: 6");
                oneOf(log4).info("\tSIVmac239_Test vpR: 10");
                oneOf(log4).info("\tSIVmac239_Test Pol: 86");
            }});

            File refFasta = getRefFasta();
            NtCoverageAggregator agg2 = new NtCoverageAggregator(log2, refFasta, agg1, params);
            agg2.setLogProgress(false);
            NtSnpByPosAggregator agg3 = new NtSnpByPosAggregator(log3, refFasta, agg1, params);
            agg3.setLogProgress(false);
            AASnpByCodonAggregator agg4 = new AASnpByCodonAggregator(log4, refFasta, agg1, params);
            agg4.setLogProgress(false);
            AASnpByReadAggregator agg5 = new AASnpByReadAggregator(_log, refFasta, agg1, params);
            agg5.setLogProgress(false);

            BamIterator bi = new BamIterator(_bam, _refFasta, _log);
            bi.addAggregators(Arrays.asList((AlignmentAggregator)agg2, agg3, agg4, agg5));
            bi.iterateReads();

            //TODO: should create methods that expose output w/o needing DB
            AnalysisModelImpl m = new AnalysisModelImpl();
            m.setContainer(_project.getId());
            Table.insert(TestContext.get().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES), m);

            agg2.writeOutput(TestContext.get().getUser(), _project, m);
            TableSelector coverage = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_COVERAGE), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long coverageCount = coverage.getRowCount();
            Assert.assertEquals("Incorrect coverage count", 8892L, coverageCount);

            agg3.writeOutput(TestContext.get().getUser(), _project, m);
            TableSelector ntPos = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS), PageFlowUtil.set("ref_nt_position", "ref_nt_insert_index", "adj_depth", "readcount"), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long ntPosCount = ntPos.getRowCount();
            Assert.assertEquals("Incorrect NT pos count", 240L, ntPosCount);

            agg4.writeOutput(TestContext.get().getUser(), _project, m);
            TableSelector aaByCodon = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON), new SimpleFilter(FieldKey.fromString("analysis_id"), m.getRowId()), null);
            long aaByCodonCount = aaByCodon.getRowCount();
            Assert.assertEquals("Incorrect AA codon count", 259L, aaByCodonCount);

            List<Map<String, Object>> results = agg5.getResults(TestContext.get().getUser(), _project, m);


            //TODO: write test

        }

        private static File getSampleDataDir()
        {
            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
            DirectoryResource resource = (DirectoryResource)module.getModuleResolver().lookup(Path.parse("sampledata"));
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
            if (_refFasta != null)
            {
                return _refFasta;
            }

            File file = new File(getSampleDataDir(), "Ref_DB.fasta");
            if (!file.exists())
                throw new FileNotFoundException("File not found: " + file.getPath());

            RefNtSequenceModel nt = SequenceIntegrationTests.ensureSivMac239(_project, _log);
            _refNtId = nt.getRowid();

            File output = new File(_pipelineRoot, "Ref_DB.fasta");
            File index = new File(_pipelineRoot, "Ref_DB.fasta.fai");
            File idFile = new File(_pipelineRoot, "Ref_DB.idKey.txt");
            if (output.exists())
            {
                output.delete();
            }

            if (idFile.exists())
            {
                idFile.delete();
            }

            if (index.exists())
            {
                index.delete();
            }

            try (PrintWriter writer = PrintWriters.getPrintWriter(idFile))
            {
                FileUtil.copyFile(file, new File(_pipelineRoot, "Ref_DB.fasta"));
                FileUtil.copyFile(new File(getSampleDataDir(), "Ref_DB.fasta.fai"), new File(_pipelineRoot, "Ref_DB.fasta.fai"));

                //create the name resolving file:
                writer.write("RowId\tName\n");
                writer.write(nt.getRowid() + "\t" + nt.getName() + "\n");
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            return output;
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
        catch (RuntimeSQLException e)
        {
            throw  new PipelineJobException(e);
        }
    }
}