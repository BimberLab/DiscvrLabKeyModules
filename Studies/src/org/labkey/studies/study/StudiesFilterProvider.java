package org.labkey.studies.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.query.TabbedReportFilterProvider;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.studies.StudiesModule;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudiesFilterProvider implements TabbedReportFilterProvider
{
    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(StudiesModule.class));
    }

    @Override
    public Collection<ClientDependency> getClientDependencies()
    {
        return List.of(ClientDependency.fromPath("studies/panel/StudiesFilterType.js"));
    }

    @Override
    public String getXType()
    {
        return "studies-filtertype";
    }

    @Override
    public String getLabel()
    {
        return "Study";
    }

    @Override
    public String getInputValue()
    {
        return "studies";
    }

    @Override
    public @NotNull Map<String, FieldKey> getAdditionalFieldKeys(TableInfo ti, TabbedReportItem tri, Map<String, FieldKey> overrides)
    {
        Map<String, FieldKey> ret = new HashMap<>();

        if (overrides.get("studyAssignmentFieldKey") == null)
        {
            FieldKey subject = tri.getSubjectIdFieldKey();
            if (subject != null)
            {
                subject = subject.getParent();
            }

            FieldKey fk = FieldKey.fromString(subject, "projects/allStudies");
            Map<FieldKey, ColumnInfo> colMap = tri.getQueryCache().getColumns(ti, PageFlowUtil.set(fk));
            if (colMap.containsKey(fk))
                ret.put("studyAssignmentFieldKey", colMap.get(fk).getFieldKey());
        }

        return ret;
    }
}
