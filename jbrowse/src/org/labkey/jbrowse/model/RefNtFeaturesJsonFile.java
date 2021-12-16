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

public class RefNtFeaturesJsonFile extends DbBackedJsonFile
{
    public RefNtFeaturesJsonFile(int genomeId, boolean createDatabaseRecordsIfNeeded, User user)
    {
        super(genomeId, "Reference Features", "ntFeatures", createDatabaseRecordsIfNeeded, user);
    }

    @Override
    protected TableSelector getSelector()
    {
        List<Integer> referenceIds = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_members"), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), _genomeId), null).getArrayList(Integer.class);
        return new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("ref_nt_features"), new SimpleFilter(FieldKey.fromString("ref_nt_id"), referenceIds, CompareType.IN), new Sort("ref_nt_id/name,nt_start"));
    }

    @Override
    protected void printGtf(PrintWriter writer, Logger log)
    {
        getSelector().forEach(rs -> {
            String name = rs.getString("name");
            int refNtId = rs.getInt("ref_nt_id");
            RefNtSequenceModel ref = RefNtSequenceModel.getForRowId(refNtId);
            String refName = ref.getName();

            String featureId = refName + "_" + name;
            writer.write(StringUtils.join(new String[]{refName, "ReferenceNTFeatures", rs.getString("category"), rs.getString("nt_start"), rs.getString("nt_stop"), ".", "+", ".", "ID=" + featureId + ";Note="}, '\t') + '\n');
        });
    }
}
