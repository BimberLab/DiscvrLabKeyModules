package org.labkey.sequenceanalysis;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;
import org.labkey.sequenceanalysis.pipeline.SequenceOutputHandlerJob;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 9/18/2016.
 */
public class OutputIntegrationTests
{

    public static class VariantProcessingTest extends SequenceIntegrationTests.AbstractAnalysisPipelineTestCase
    {
        private static final String PROJECT_NAME = "VariantProcessingTestProject";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        @Override
        protected String getProjectName()
        {
            return PROJECT_NAME;
        }

        @Test
        public void testVariantProcessing() throws Exception
        {
            if (!isExternalPipelineEnabled())
                return;

            //create genome
            Integer genomeId = createSavedLibrary();

            //create VCF, import as outputfile
            String basename = "TestFile_" + FileUtil.getTimestamp();
            File vcf = new File(_pipelineRoot, basename + ".vcf.gz");
            Integer outputFileId = createTestVcf(genomeId, vcf);

            //make job params
            String jobName = "TestVariantProcessing";
            JSONObject config = substituteParams(new File(_sampleData, VARIANT_JOB), jobName);
            Set<Integer> outputFileIds = Collections.singleton(outputFileId);

            TableInfo ti = QueryService.get().getUserSchema(TestContext.get().getUser(), _project, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES, null);
            assertNotNull("No FK found", ti.getColumn("runId").getFk());
            assertEquals("Job FK not found", 1, QueryService.get().getColumns(ti, Collections.singletonList(FieldKey.fromString("runId/jobid/job"))).size());

            Set<PipelineJob> jobs = createOutputHandlerJob(jobName, config, ProcessVariantsHandler.class, outputFileIds);
            waitForJobs(jobs);

            for (PipelineJob j : jobs)
            {
                SequenceOutputHandlerJob job = (SequenceOutputHandlerJob)j;

                Set<File> extraFiles = new HashSet<>();
                extraFiles.add(new File(job.getAnalysisDirectory(), jobName + "." + outputFileId + ".log"));
                extraFiles.add(new File(job.getAnalysisDirectory(), "sequenceOutput.json"));
                extraFiles.add(new File(job.getAnalysisDirectory(), "sequenceSupport.json.gz"));
                extraFiles.add(new File(job.getAnalysisDirectory(), "gatk.ped"));
                extraFiles.add(new File(job.getAnalysisDirectory(), basename + ".gfiltered.selectVariants.annotated.filtered.vcf.gz"));
                extraFiles.add(new File(job.getAnalysisDirectory(), basename + ".gfiltered.selectVariants.annotated.filtered.vcf.gz.tbi"));
                extraFiles.add(new File(job.getAnalysisDirectory(), job.getBaseName() + ".pipe.xar.xml"));

                verifyFileOutputs(job.getAnalysisDirectory(), extraFiles);

                //verify outputfile created:
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("runId/jobid/job"), job.getJobGUID()), null);
                List<Integer> rowIDs = ts.getArrayList(Integer.class);
                assertEquals("outputs not created", 1, rowIDs.size());
                //SequenceOutputFile so = SequenceOutputFile.getForId(rowIDs.get(0));
            }
        }

        protected Set<PipelineJob> createOutputHandlerJob(String jobName, JSONObject config, Class handlerClass, Set<Integer> outputFileIDs) throws Exception
        {
            Map<String, Object> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("handlerClass", handlerClass.getCanonicalName());
            json.put("outputFileIds", outputFileIDs);
            json.put("params", config.toString());
            json.put("jobName", jobName);

            String requestContent = json.toString();

            HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(), DetailsURL.fromString("/sequenceanalysis/runSequenceHandler.view").copy(_project).getActionURL(), _context.getUser(), headers, requestContent);

            MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
            JSONObject responseJson = new JSONObject(response.getContentAsString());
            if (response.getStatus() != HttpServletResponse.SC_OK)
                throw new RuntimeException("Problem creating pipeline job: " + responseJson.getString("exception"));

            JSONArray guidList = responseJson.getJSONArray("jobGUIDs");
            assert guidList.length() >= 1;

            Set<PipelineJob> ret = new HashSet<>();
            for (int i = 0; i < guidList.length(); i++)
            {
                ret.add(PipelineJobService.get().getJobStore().getJob(guidList.getString(i)));
            }

            return ret;
        }

        private int createTestVcf(int genomeId, File vcf)
        {
            if (vcf.exists())
            {
                vcf.delete();
            }

            VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
            builder.setOutputFile(vcf);

            Set<VCFHeaderLine> metaLines = new HashSet<>();
            VCFStandardHeaderLines.addStandardInfoLines(metaLines, false, Arrays.asList(
                    VCFConstants.END_KEY,
                    VCFConstants.DBSNP_KEY,
                    VCFConstants.DEPTH_KEY,
                    VCFConstants.STRAND_BIAS_KEY,
                    VCFConstants.ALLELE_FREQUENCY_KEY,
                    VCFConstants.ALLELE_COUNT_KEY,
                    VCFConstants.ALLELE_NUMBER_KEY,
                    VCFConstants.MAPPING_QUALITY_ZERO_KEY,
                    VCFConstants.RMS_MAPPING_QUALITY_KEY,
                    VCFConstants.SOMATIC_KEY
            ));

            VCFStandardHeaderLines.addStandardFormatLines(metaLines, false, Arrays.asList(
                    VCFConstants.GENOTYPE_KEY,
                    VCFConstants.GENOTYPE_QUALITY_KEY,
                    VCFConstants.DEPTH_KEY,
                    VCFConstants.GENOTYPE_PL_KEY,
                    VCFConstants.GENOTYPE_ALLELE_DEPTHS,
                    VCFConstants.GENOTYPE_FILTER_KEY,
                    VCFConstants.PHASE_QUALITY_KEY
            ));

            VCFHeader header = new VCFHeader(metaLines, List.of("Sample1"));

            Integer dataId = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), genomeId), null).getObject(Integer.class);
            ExpData data = ExperimentService.get().getExpData(dataId);

            File dictFile = new File(data.getFile().getParent(), FileUtil.getBaseName(data.getFile().getName()) + ".dict");
            if (dictFile.exists())
            {
                SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(dictFile.toPath());
                builder.setReferenceDictionary(dict);
                header.setSequenceDictionary(dict);
            }

            try (VariantContextWriter writer = builder.build())
            {
                writer.writeHeader(header);

                VariantContextBuilder vcb = new VariantContextBuilder();
                vcb.chr("SIVmac239_Test");
                vcb.start(10);
                vcb.stop(10);
                vcb.alleles(Collections.singletonList(Allele.create("T", true)));
                vcb.genotypes(GenotypesContext.NO_GENOTYPES);

                writer.add(vcb.make());
            }

            ExpData d = createExpData(vcf);
            Map<String, Object> params = new CaseInsensitiveHashMap<>();
            params.put("name", "TestVcf");
            params.put("description", "Description");

            params.put("library_id", genomeId);
            params.put("category", "VCF File");
            params.put("dataId", d.getRowId());
            params.put("container", _project.getId());
            params.put("created", new Date());
            params.put("createdby", TestContext.get().getUser().getUserId());
            params.put("modified", new Date());
            params.put("modifiedby", TestContext.get().getUser().getUserId());

            params = Table.insert(TestContext.get().getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), params);

            return (Integer)params.get("rowid");
        }
    }
}
