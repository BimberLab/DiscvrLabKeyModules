package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.run.util.ImmunoGenotypingWrapper;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 7/31/2017.
 */
public class ImmunoGenotypingAnalysis extends AbstractCommandPipelineStep<ImmunoGenotypingWrapper> implements AnalysisStep
{
    public ImmunoGenotypingAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new ImmunoGenotypingWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<ImmunoGenotypingAnalysis>
    {
        public Provider()
        {
            super("ImmunoGenotyping", "ImmunoGenotyping", null, "If selected, each alignment will be inspected, and those alignments with perfect matches to the reference.  A report will be generated summarizing these matches, per read.", getDefaultParams(true), null, null);
        }

        @Override
        public ImmunoGenotypingAnalysis create(PipelineContext ctx)
        {
            return new ImmunoGenotypingAnalysis(this, ctx);
        }
    }

    public static List<ToolParameterDescriptor> getDefaultParams(boolean includeExport)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();
        ret.addAll(Arrays.asList(ToolParameterDescriptor.create("--minMappingQuality", "Minimum Mapping Qual", "If provided, any alignment with a mapping quality lower than this value will be discarded", "ldk-integerfield", new JSONObject()
                {{
                    put("minValue", 0);
                }}, 0),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--requireValidPair"), "requireValidPair", "Only Import Valid Pairs", "If selected, only alignments consisting of valid forward/reverse pairs will be imported.  Do not check this unless you are using paired-end sequence.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, null),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minReadCountForRef"), "minReadCountForRef", "Min Read # Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this many reads across each sample.  This can be a way to reduce ambiguity among allele calls.", "ldk-integerfield", new JSONObject()
                {{
                    put("minValue", 0);
                }}, 5),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minPctForRef"), "minPctForRef", "Min Read Pct Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total from each sample.  This can be a way to reduce ambiguity among allele calls.  Value should between 0-1.", "ldk-numberfield", new JSONObject()
                {{
                    put("minValue", 0);
                    put("maxValue", 1);
                    put("decimalPrecision", 4);
                }}, 0.005),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minPctForLineageFiltering"), "minPctForLineageFiltering", "Min Pct For Lineage Filtering", "If a value is provided, each group of allele hits will be categorized by lineage.  Any groupings representing more than the specified percent of reads from that lineage will be included.  Per lineage, we will also find the intersect of all groups.  If a set of alleles is common to all groups, only these alleles will be kept and the others discarded.", "ldk-numberfield", new JSONObject()
                {{
                    put("minValue", 0);
                    put("maxValue", 1);
                    put("decimalPrecision", 4);
                }}, 0.025),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minAlignmentLength"), "minAlignmentLength", "Min Alignment Length", "If a value is provided, any alignment with a length less than this value will be discarded.", "ldk-integerfield", new JSONObject()
                {{
                    put("minValue", 0);
                }}, 40),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--mismatchesTolerated"), "mismatchesTolerated", "Mismatches Tolerated", "If a value is provided, any alignment with greater than this number of mismatches will be discarded.", "ldk-integerfield", new JSONObject()
                {{
                    put("minValue", 0);
                }}, 0),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minPctForExport"), "minPctForExport", "Min Pct For Export", "For a group to be exported, it must represent at least this fraction of total mapped reads.", "ldk-numberfield", new JSONObject()
                {{
                    put("minValue", 0);
                    put("maxValue", 1);
                    put("decimalPrecision", 4);
                }}, 0.001),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minReadCountForExport"), "minReadCountForExport", "Min Read Count For Export", "If a value is provided, any group must have at least this many reads to be exported.", "ldk-integerfield", new JSONObject()
                {{
                    put("minValue", 0);
                }}, 5)
        ));

        return ret;
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        for (ReferenceGenome genome : support.getCachedGenomes())
        {
            File output = getLineageMapFile(getPipelineCtx(), genome);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                List<Integer> refNtIds = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), genome.getGenomeId()), null).getArrayList(Integer.class);
                new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), PageFlowUtil.set("rowid", "name", "lineage"), new SimpleFilter(FieldKey.fromString("rowid"), refNtIds, CompareType.IN), null).forEachResults(rs -> {
                    if (rs.getString(FieldKey.fromString("lineage")) != null)
                    {
                        writer.writeNext(new String[]{rs.getString(FieldKey.fromString("name")), rs.getString(FieldKey.fromString("lineage"))});
                    }
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File outputPrefix = getSBTSummaryFileBasename(outDir, inputBam);
        File outputTxt = new File(outputPrefix.getPath() + ".genotypes.txt");
        if (outputTxt.exists())
        {
            getPipelineCtx().getLogger().info("Processing SBT output: " + outputTxt.getPath());
            doImport(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), model, outputTxt, referenceFasta, getPipelineCtx().getLogger());
        }
        else
        {
            getPipelineCtx().getLogger().error("SBT output not found: " + outputTxt.getPath());
        }

        File summary = new File(outputPrefix.getPath() + ".summary.txt");
        if (summary.exists())
        {
            try (BufferedReader reader = Readers.getReader(summary))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    getPipelineCtx().getLogger().info(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return null;
    }

    private File getLineageMapFile(PipelineContext ctx, ReferenceGenome referenceGenome)
    {
        return new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        //ensure queryName sorted:
        String basename = FileUtil.getBaseName(inputBam);
        File queryNameSortBam;
        try
        {
            if (SequencePipelineService.get().getBamSortOrder(inputBam) != SAMFileHeader.SortOrder.queryname)
            {
                queryNameSortBam = new SamSorter(getPipelineCtx().getLogger()).execute(inputBam, new File(outputDir, basename + ".querySort.bam"), SAMFileHeader.SortOrder.queryname);

                output.addIntermediateFile(queryNameSortBam);
            }
            else
            {
                queryNameSortBam = inputBam;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> options = new ArrayList<>();
        File lineageMapFile = getLineageMapFile(getPipelineCtx(), referenceGenome);
        if (lineageMapFile.exists())
        {
            options.add("-referenceToLineageFile");
            options.add(lineageMapFile.getPath());

            output.addIntermediateFile(lineageMapFile);
        }
        else
        {
            getPipelineCtx().getLogger().debug("lineage map not found, skipping");
        }

        options.addAll(getClientCommandArgs());

        File outputTxt = getSBTSummaryFileBasename(outputDir, inputBam);
        getWrapper().execute(queryNameSortBam, referenceGenome.getWorkingFastaFile(), outputTxt, options);

        output.addOutput(outputTxt, "ImmunoGenotyping Data");

        return output;
    }

    protected File getSBTSummaryFileBasename(File outputDir, File bam)
    {
        return new File(outputDir, FileUtil.getBaseName(bam));
    }

    public void doImport(User u, Container c, AnalysisModel model, File output, File refFasta, Logger log) throws PipelineJobException
    {
        try (CSVReader reader = new CSVReader(Readers.getReader(output), '\t'))
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                //delete existing records
                TableInfo ti_summary = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY);
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
                Table.delete(ti_summary, filter);

                TableInfo ti_junction = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION);
                Table.delete(ti_junction, filter);

                //insert new
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    if (line[0].startsWith("RefNames"))
                    {
                        continue;
                    }

                    Map<String, Object> row = new HashMap<>();
                    row.put("analysis_id", model.getAnalysisId());
                    row.put("file_id", model.getAlignmentFile());

                    //TODO: track forward/reverse or other data?
                    row.put("total", line[2]);

                    row.put("container", c.getEntityId());
                    row.put("createdby", u.getUserId());
                    row.put("modifiedby", u.getUserId());
                    row.put("created", new Date());
                    row.put("modified", new Date());
                    Map<String, Object> newRow = Table.insert(u, ti_summary, row);

                    if (!StringUtils.isEmpty(line[0]))
                    {
                        String[] names = line[0].split(",");
                        ReferenceLibraryHelperImpl helper = new ReferenceLibraryHelperImpl(refFasta, log);
                        for (String refName : names)
                        {
                            Integer refId = helper.resolveSequenceId(refName);
                            if (refId == null)
                            {
                                log.error("unknown reference id: [" + refName + "]");
                            }

                            Map<String, Object> junction_row = new HashMap<>();
                            junction_row.put("analysis_id", model.getAnalysisId());
                            junction_row.put("ref_nt_id", refId);
                            junction_row.put("alignment_id", newRow.get("rowid"));
                            junction_row.put("status", true);
                            junction_row.put("container", c.getEntityId());
                            junction_row.put("createdby", u.getUserId());
                            junction_row.put("modifiedby", u.getUserId());
                            junction_row.put("created", new Date());
                            junction_row.put("modified", new Date());
                            Table.insert(u, ti_junction, junction_row);
                        }
                    }
                }

                transaction.commit();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}