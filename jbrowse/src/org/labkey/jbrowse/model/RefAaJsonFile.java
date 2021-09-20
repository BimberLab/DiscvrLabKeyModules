package org.labkey.jbrowse.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.JBrowseManager;

import java.io.PrintWriter;
import java.util.List;

public class RefAaJsonFile extends DbBackedJsonFile
{
    public RefAaJsonFile(int genomeId, boolean createDatabaseRecordsIfNeeded, User user)
    {
        super(genomeId, "Reference Proteins", "codingRegions", createDatabaseRecordsIfNeeded, user);
    }

    @Override
    protected TableSelector getSelector()
    {
        List<Integer> referenceIds = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_members"), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), _genomeId), null).getArrayList(Integer.class);
        return new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("ref_aa_sequences"), new SimpleFilter(FieldKey.fromString("ref_nt_id"), referenceIds, CompareType.IN), new Sort("ref_nt_id/name,start_location"));
    }

    @Override
    protected void printGtf(PrintWriter writer, Logger log)
    {
        getSelector().forEach(rs -> {
            String name = rs.getString("name");
            int refNtId = rs.getInt("ref_nt_id");
            boolean isComplement = rs.getObject("isComplement") != null && rs.getBoolean("isComplement");
            String exons = StringUtils.trimToNull(rs.getString("exons"));
            if (exons == null)
            {
                return;
            }

            RefNtSequenceModel ref = RefNtSequenceModel.getForRowId(refNtId);
            String refName = ref.getName();

            String[] tokens = StringUtils.split(exons, ";");

            String featureId = refName + "_" + name;
            String strand = isComplement ? "-" : "+";
            String[] lastExon = StringUtils.split(tokens[tokens.length - 1], "-");
            if (lastExon.length != 2)
            {
                return;
            }

            writer.write(StringUtils.join(new String[]{refName, "ReferenceAA", "gene", rs.getString("start_location"), lastExon[1], ".", strand, ".", "ID=" + featureId + ";Note="}, '\t') + System.getProperty("line.separator"));

            for (String exon : tokens)
            {
                String[] borders = StringUtils.split(exon, "-");
                if (borders.length != 2)
                {
                    log.error("improper exon: " + exon);
                    return;
                }

                writer.write(StringUtils.join(new String[]{refName, "ReferenceAA", "CDS", borders[0], borders[1], ".", strand, ".", "Parent=" + featureId}, '\t') + System.getProperty("line.separator"));
            }
        });
    }
}
