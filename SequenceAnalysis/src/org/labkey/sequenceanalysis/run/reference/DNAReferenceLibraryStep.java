package org.labkey.sequenceanalysis.run.reference;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceLibraryStep;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.pipeline.AlignmentInitTask;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 8:32 PM
 */
public class DNAReferenceLibraryStep extends AbstractPipelineStep implements ReferenceLibraryStep
{
    public DNAReferenceLibraryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<DNAReferenceLibraryStep>
    {
        public Provider()
        {
            super("DNA", "DNA Sequences", null, "Select this option to construct a new reference genome by filtering sequences in the server's DNA DB", Arrays.asList(
                    ToolParameterDescriptor.create("species", "Species", "Select the desired species to use in the reference genome", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "laboratory");
                            put("queryName", "species");
                            put("multiSelect", true);
                            put("displayField", "common_name");
                            put("valueField", "common_name");
                        }}, null),
                    ToolParameterDescriptor.create("subset", "Subset", "Select the DNA regions to use in the reference genome.  Leave blank for all.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "sequenceanalysis");
                            put("queryName", "dna_region");
                            put("multiSelect", true);
                            put("displayField", "region");
                            put("valueField", "region");
                        }}, null),
                    ToolParameterDescriptor.create("mol_type", "Molecule Type", "Select the desired molecules types to use in the reference genome.  Leave blank for all.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "laboratory");
                            put("queryName", "dna_mol_type");
                            put("multiSelect", true);
                            put("displayField", "mol_type");
                            put("valueField", "mol_type");
                        }}, null),
                    ToolParameterDescriptor.create("geographic_origin", "Geographic Origin", "Select the desired geographic origins to use in the reference genome.  Leave blank for all.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "laboratory");
                            put("queryName", "geographic_origins");
                            put("multiSelect", true);
                            put("displayField", "origin");
                            put("valueField", "origin");
                        }}, null),
                    ToolParameterDescriptor.create("locus", "Loci", "Select the desired loci to use in the reference genome.  Leave blank for all.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "sequenceanalysis");
                            put("queryName", "dna_loci");
                            put("multiSelect", true);
                            put("displayField", "locus");
                            put("valueField", "locus");
                        }}, null),
                    ToolParameterDescriptor.create(null, null, null, "ldk-linkbutton", new JSONObject()
                    {{
                            put("text", "Click here to view reference sequences");
                            put("linkCls", "labkey-text-link");
                            put("linkTarget", "_blank");
                            put("isToolParam", false);
                            put("href", "js:LABKEY.ActionURL.buildURL('query', 'executeQuery.view', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'ref_nt_sequences'})");
                        }}, null)
            ), null, null);
        }

        @Override
        public DNAReferenceLibraryStep create(PipelineContext ctx)
        {
            return new DNAReferenceLibraryStep(this, ctx);
        }
    }

    protected File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        return new File(outputDirectory, "Ref_DB.fasta");
    }

    private File getExpectedIdKeyFile(File outputDirectory) throws PipelineJobException
    {
        return SequenceAnalysisService.get().getLibraryHelper(getExpectedFastaFile(outputDirectory)).getIdKeyFile();
    }

    @Override
    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException
    {
        getPipelineCtx().getLogger().info("Downloading Reference DB:");
        getPipelineCtx().getLogger().info("\tUsing filters:");

        SimpleFilter filter = getReferenceFilter();
        for (SimpleFilter.FilterClause fc : filter.getClauses())
        {
            Object[] vals = fc.getParamVals();
            StringBuilder sb = new StringBuilder();
            String comparison = ((CompareType.CompareClause) fc).getCompareType().getDisplayValue();
            String delim = "";
            int i = 0;
            for (FieldKey fk : fc.getFieldKeys())
            {
                sb.append(delim).append(fk.getLabel()).append(" ").append(comparison).append(" ").append(vals[i]);

                i++;
                delim = " and ";
            }
            getPipelineCtx().getLogger().info("\t\t" + sb.toString());
        }

        TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        TableSelector ts = new TableSelector(ti, filter, null);
        List<RefNtSequenceModel> rows = ts.getArrayList(RefNtSequenceModel.class);

        getPipelineCtx().getLogger().info("\tTotal reference sequences: " + rows.size());
        if (rows.isEmpty())
        {
            throw new PipelineJobException("There were no reference sequences returned, unable to perform alignment");
        }

        File refFasta = getExpectedFastaFile(outputDirectory);
        File idKey = getExpectedIdKeyFile(outputDirectory);
        getPipelineCtx().getLogger().debug("writing FASTA to: " + refFasta.getPath());

        long totalBases = 0;
        for (RefNtSequenceModel row : rows)
        {
            totalBases += row.getSeqLength();
            if (totalBases > 1e9){
                throw new PipelineJobException("The DNA filters you selected returned a large amount of data.  It would be better to first create a reference genome using selected sequence and then run this alignment against that");
            }
        }

        try (PrintWriter writer = PrintWriters.getPrintWriter(refFasta); PrintWriter idWriter = PrintWriters.getPrintWriter(idKey))
        {
            if (!refFasta.exists())
                refFasta.createNewFile();

            idWriter.write("RowId\tName\tAccession\tStart\tStop" + System.getProperty("line.separator"));

            for (RefNtSequenceModel row : rows)
            {
                String name = row.getName();
                name = name.replaceAll(":| ", "_"); //replace problem chars
                writer.write(">" + name + System.getProperty("line.separator"));
                writer.write(row.getSequence() + System.getProperty("line.separator"));

                idWriter.write(row.getRowid() + "\t" + row.getName() + System.getProperty("line.separator"));
                row.clearCachedSequence();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        ReferenceLibraryOutputImpl output = new ReferenceLibraryOutputImpl(new ReferenceGenomeImpl(refFasta, null, null, null));
        output.addOutput(refFasta, IndexOutputImpl.REFERENCE_DB_FASTA);
        output.addOutput(idKey, AlignmentInitTask.ID_KEY_FILE);
        output.addOutput(outputDirectory, "Reference Genome Folder");

        return output;
    }

    protected SimpleFilter getReferenceFilter() throws PipelineJobException
    {
        SimpleFilter filter = new SimpleFilter();

        List<ToolParameterDescriptor> descriptors = getProvider().getParameters();
        for (ToolParameterDescriptor desc : descriptors)
        {
            String val = desc.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx());
            if ("All".equalsIgnoreCase(val))
            {
                continue;
            }

            if (StringUtils.trimToNull(val) == null)
            {
                continue;
            }

            List<Object> vals = new ArrayList<>();
            try
            {
                JSONArray arr = new JSONArray(val);
                vals.addAll(arr.toList());
            }
            catch (JSONException e)
            {
                vals.add(val);
            }

            if (vals.size() > 1)
            {
                filter.addClause(new CompareType.CompareClause(FieldKey.fromString(desc.getName()), CompareType.IN, Arrays.asList(vals)));
            }
            else if (vals.size() == 1)
            {
                filter.addClause(new CompareType.CompareClause(FieldKey.fromString(desc.getName()), CompareType.EQUAL, vals.get(0)));
            }
        }

        return filter;
    }
}
