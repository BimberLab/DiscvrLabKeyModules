package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NextCladeHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public static final String NEXTCLADE_JSON = "NextClade JSON";

    public NextCladeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), "NextClade", "Runs NextClade, a tool for processing SARS-CoV-2 data", null, Collections.emptyList());
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && SequenceUtil.FILETYPE.fasta.getFileType().isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new PangolinHandler.Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputFiles, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            Map<Integer, SequenceOutputFile> readsetToInput = inputFiles.stream().collect(Collectors.toMap(SequenceOutputFile::getReadset, x -> x));

            job.getLogger().info("Parsing NextClade JSON:");
            for (SequenceOutputFile so : outputsCreated)
            {
                if (!NEXTCLADE_JSON.equals(so.getCategory()))
                {
                    continue;
                }

                if (so.getReadset() == null)
                {
                    throw new PipelineJobException("Expected all file to have a readset");
                }

                SequenceOutputFile parent = readsetToInput.get(so.getReadset());
                if (parent == null)
                {
                    throw new PipelineJobException("Unable to find parent for output: " + so.getRowid());
                }

                processAndImportNextCladeAa(job, so.getFile(), parent.getAnalysis_id(), so.getLibrary_id(), so.getDataId(), so.getReadset(), parent.getFile(), true);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                File nextCladeJson = runNextClade(so.getFile(), ctx.getLogger(), ctx.getFileManager(), ctx.getOutputDir());
                ctx.getFileManager().addSequenceOutput(nextCladeJson, "Nextclade: " + so.getName(), NEXTCLADE_JSON, so.getReadset(), null, so.getLibrary_id(), null);
            }
        }
    }

    public static File getJsonFile(File outputDir, File consensusFasta)
    {
        return new File(outputDir, FileUtil.getBaseName(consensusFasta) + ".json");
    }

    public static File runNextClade(File consensusFasta, Logger log, PipelineOutputTracker tracker, File outputDir) throws PipelineJobException
    {
        if (!consensusFasta.getParentFile().equals(outputDir))
        {
            try
            {
                File consensusFastaLocal = new File(outputDir, consensusFasta.getName());
                FileUtils.copyFile(consensusFasta, consensusFastaLocal);
                tracker.addIntermediateFile(consensusFastaLocal);
                consensusFasta = consensusFastaLocal;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        File jsonFile = getJsonFile(outputDir, consensusFasta);

        File localBashScript = new File(outputDir, "dockerWrapper.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull neherlab/nextclade");
            writer.println("sudo $DOCKER run --rm=true \\");

            if (SequencePipelineService.get().getMaxThreads(log) != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_THREADS \\");
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-u $UID \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t-w /work \\");
            writer.println("\tneherlab/nextclade \\");
            writer.println("\t nextclade --input-fasta '/work/" + consensusFasta.getName() + "' --output-json '/work/" + jsonFile.getName() + "'");
            writer.println("");
            writer.println("echo 'Bash script complete'");
            writer.println("");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(log);
        rWrapper.setWorkingDir(outputDir);
        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));

        localBashScript.delete();

        if (!jsonFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + jsonFile.getPath());
        }

        return jsonFile;
    }

    private static JSONObject parseNextClade(File jsonFile) throws PipelineJobException
    {
        try (InputStream is = IOUtil.openFileForReading(jsonFile))
        {
            JSONArray samples = new JSONArray(IOUtil.readFully(is));
            if (samples.length() != 1)
            {
                throw new PipelineJobException("Expected a single sample, was: " + samples.length());
            }

            return samples.getJSONObject(0);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static void processAndImportNextCladeAa(PipelineJob job, File jsonFile, int analysisId, int libraryId, int alignmentId, int readsetId, File consensusVCF, boolean dbImport) throws PipelineJobException
    {
        JSONObject sample = parseNextClade(jsonFile);

        ReferenceGenome genome = SequenceAnalysisService.get().getReferenceGenome(libraryId, job.getUser());
        String clade = sample.getString("clade");
        saveClade(clade, analysisId, alignmentId, readsetId, job);

        if (!dbImport)
        {
            job.getLogger().info("DB Import not selected, will not import AA SNPs");
            return;
        }

        ViralSnpUtil.deleteExistingValues(job, analysisId, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, null);

        if (!sample.containsKey("aaSubstitutions"))
        {
            job.getLogger().info("JSON does not contain aaSubstitutions, skipping");
            return;
        }

        JSONArray aaSubstitutions = sample.getJSONArray("aaSubstitutions");
        Map<Integer, List<VariantContext>> consensusMap = ViralSnpUtil.readVcfToMap(consensusVCF);

        TableInfo aaTable = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON);

        //This is SARS-CoV-2 specific, so this is a safe assumption
        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), genome.getGenomeId()), null);
        if (ts.getRowCount() != 1)
        {
            throw new PipelineJobException("Expected reference genome to have single sequence");
        }

        int refNtId = ts.getObject(Integer.class);

        for (int i=0;i<aaSubstitutions.length();i++)
        {
            JSONObject aa = aaSubstitutions.getJSONObject(i);
            int pos = aa.getInt("codon");
            pos = pos + 1; //make 1-based

            String aaName = aa.getString("gene");

            JSONObject range = aa.getJSONObject("nucRange");
            List<Integer> positions = new ArrayList<>();
            //Range is 0-based
            for (int p = range.getInt("begin") + 1;p <= range.getInt("end"); p++)
            {
                positions.add(p);
            }

            List<VariantContext> vcList = new ArrayList<>();
            for (Integer ntPos : positions)
            {
                if (consensusMap.containsKey(ntPos))
                {
                    vcList.addAll(consensusMap.get(ntPos));
                }
            }

            if (vcList.isEmpty())
            {
                //NOTE: if this is an indel, upstream variants could cause this:
                job.getLogger().info("No identical position match found, inspecting overlapping variants (" + consensusMap.size() + "):");
                for (int ntPos : consensusMap.keySet())
                {
                    for (VariantContext vc : consensusMap.get(ntPos))
                    {
                        if (vc.overlaps(new Interval(vc.getContig(), positions.get(0), positions.get(positions.size() - 1))))
                        {
                            vcList.add(vc);
                        }
                    }
                }
            }

            // Try to recover frameshifts:
            if (vcList.isEmpty())
            {
                List<Integer> potentialFs = new ArrayList<>();
                potentialFs.addAll(consensusMap.keySet().stream().filter(x -> x < positions.get(0)).collect(Collectors.toList()));
                Collections.sort(potentialFs, Collections.reverseOrder());
                OUTER: for (int ntPos : potentialFs)
                {
                    if (positions.get(0) - ntPos > 100)
                    {
                        break;
                    }

                    for (VariantContext vc : consensusMap.get(ntPos))
                    {
                        if (vc.isIndel())
                        {
                            job.getLogger().info("Inferred associated NT is: " + vc.getStart());
                            job.getLogger().info("for pos: " + aa.toString());
                            vcList.add(vc);
                            break OUTER;
                        }
                    }
                }
            }

            if (vcList.isEmpty())
            {
                job.getLogger().error("Cannot find matching NT SNP: " + aa.toString());
                throw new PipelineJobException("Expected variant for AA position: " + aaName + " " + pos);
            }

            Double depth;
            Double alleleDepth;
            Double af;
            Double dp;

            if (vcList.size() == 1)
            {
                depth = (double)vcList.get(0).getAttributeAsInt("GATK_DP", 0);

                List<Integer> depths = vcList.get(0).getAttributeAsIntList("DP4", 0);
                alleleDepth = (double)depths.get(2) + depths.get(3);
                dp = (double)vcList.get(0).getAttributeAsInt("DP", 0);
                af = vcList.get(0).getAttributeAsDouble("AF", 0.0);
            }
            else
            {
                job.getLogger().warn("Multiple NT SNPs found at AA pos: " + aaName + " " + pos + ", was: " + vcList.size());
                depth = vcList.stream().mapToInt(x -> x.getAttributeAsInt("GATK_DP", 0)).summaryStatistics().getAverage();

                alleleDepth = vcList.stream().mapToDouble(x -> {
                    List<Integer> dps = x.getAttributeAsIntList("DP4", 0);
                    return dps.get(2) + dps.get(3);
                }).summaryStatistics().getAverage();

                af = vcList.stream().mapToDouble(x -> x.getAttributeAsDouble("AF", 0)).summaryStatistics().getAverage();
                dp = vcList.stream().mapToDouble(x -> x.getAttributeAsDouble("DP", 0)).summaryStatistics().getAverage();
            }

            int refAaId = ViralSnpUtil.resolveGene(refNtId, aaName);

            Map<String, Object> aaRow = new CaseInsensitiveHashMap<>();
            aaRow.put("analysis_id", analysisId);
            aaRow.put("ref_nt_id", refNtId);
            aaRow.put("ref_aa_id", refAaId);
            aaRow.put("ref_aa_position", pos);
            aaRow.put("ref_aa_insert_index", 0);
            aaRow.put("ref_aa", aa.getString("refAA"));
            aaRow.put("q_aa", aa.getString("queryAA"));
            aaRow.put("codon", aa.getString("queryCodon"));
            aaRow.put("ref_nt_positions", StringUtils.join(positions, ","));

            aaRow.put("readcount", alleleDepth);
            aaRow.put("depth", depth);
            aaRow.put("adj_depth", dp);
            aaRow.put("pct", af);

            aaRow.put("createdby", job.getUser().getUserId());
            aaRow.put("created", new Date());
            aaRow.put("modifiedby", job.getUser().getUserId());
            aaRow.put("modified", new Date());
            aaRow.put("container", job.getContainer());

            Table.insert(job.getUser(), aaTable, aaRow);
        }
    }

    private static void saveClade(String clade, int analysisId, int alignmentId, int readsetId, PipelineJob job) throws PipelineJobException
    {
        List<Map<String, Object>> toInsert = new ArrayList<>();
        Map<String, Object> row1 = new CaseInsensitiveHashMap<>();
        row1.put("dataid", alignmentId);
        row1.put("readset", readsetId);
        row1.put("analysis_id", analysisId);
        row1.put("category", "NextClade");
        row1.put("metricName", "NextCladeClade");
        row1.put("qualvalue", clade);
        row1.put("container", job.getContainer().getId());
        toInsert.add(row1);

        Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        TableInfo ti = QueryService.get().getUserSchema(job.getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
        try
        {
            ViralSnpUtil.deleteExistingMetrics(job, analysisId, "NextClade");

            BatchValidationException bve = new BatchValidationException();
            ti.getUpdateService().insertRows(job.getUser(), targetContainer, toInsert, bve, null, null);

            if (bve.hasErrors())
            {
                throw bve;
            }
        }
        catch (BatchValidationException | QueryUpdateServiceException | SQLException | DuplicateKeyException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
