/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.sequenceanalysis;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.module.Module;
import org.labkey.api.module.SimpleModuleContainerListener;
import org.labkey.api.security.User;

public class SequenceAnalysisContainerListener extends SimpleModuleContainerListener
{
    public SequenceAnalysisContainerListener(Module owner)
    {
        super(owner);
    }

    @Override
    public void containerDeleted(Container c, User u)
    {
        //note: this schema has several FKs, so the delete order is important.  to get around this, first delete from those tables:
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE alignment_id IN (select rowid from " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE container = ?)", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE container = ?", c.getEntityId()));

            transaction.commit();
        }

        //now run the core listener to make sure we delete any new tables
        super.containerDeleted(c, u);
    }
}