package org.labkey.studies.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.studies.StudiesSchema;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StudiesTriggerFactory implements TriggerFactory
{
    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(new StudyTrigger());
    }

    public static class StudyTrigger implements Trigger
    {
        @Override
        public void beforeInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            beforeInsert(table, c, user, newRow, errors, extraContext, null);
        }

        @Override
        public void beforeInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
        {
            possiblyResolveStudy(newRow, c);
        }

        @Override
        public void beforeUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            possiblyResolveStudy(newRow, c);
        }

        /**
         * This allows incoming data to specify the study using the string name, which is resolved into the rowId
         */
        private void possiblyResolveStudy(@Nullable Map<String, Object> row, Container c)
        {
            if (row == null)
            {
                return;
            }

            possiblyResolveStudy(row, c, "studyId");
            if (row.get("studyId") == null & row.get("studyName") != null)
            {
                possiblyResolveStudy(row, c, "studyName");
            }
        }

        private void possiblyResolveStudy(@Nullable Map<String, Object> row, Container c, String sourceProperty)
        {
            if (row == null)
            {
                return;
            }

            if (row.get(sourceProperty) != null & row.get(sourceProperty) instanceof String)
            {
                if (!NumberUtils.isCreatable(row.get(sourceProperty).toString()))
                {
                    Container target = c.isWorkbookOrTab() ? c.getParent() : c;
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("container"), target.getEntityId()).addCondition(FieldKey.fromString("name"), row.get(sourceProperty));
                    List<Integer> rowIds = new TableSelector(StudiesSchema.getInstance().getSchema().getTable(StudiesSchema.TABLE_STUDIES), PageFlowUtil.set("rowId"), filter, null).getArrayList(Integer.class);
                    if (rowIds.size() == 1)
                    {
                        row.put("studyId", rowIds.get(0));
                    }
                }
            }
        }
    }
}
