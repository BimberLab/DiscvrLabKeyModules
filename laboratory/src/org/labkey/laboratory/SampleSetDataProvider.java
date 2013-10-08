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
package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.laboratory.SingleNavItem;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/8/12
 * Time: 9:06 AM
 */
public class SampleSetDataProvider extends AbstractDataProvider
{
    public static final String NAME = "SampleSetDataProvider";

    public SampleSetDataProvider()
    {

    }

    public String getName()
    {
        return NAME;
    }

    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    public List<NavItem> getDataNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        //also append all sample sets in this container
        List<NavItem> navItems = new ArrayList<NavItem>();

        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(c, u, true))
        {
            navItems.add(new SampleSetNavItem(this, ss));
        }
        return navItems;
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    public Module getOwningModule()
    {
        return null;
    }

    public List<NavItem> getSummary(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(c, u, true))
        {
            SampleSetNavItem nav = new SampleSetNavItem(this, ss);
            Integer total = 0;
            if (nav.isVisible(c, u))
            {
                for (ExpMaterial m : ss.getSamples())
                {
                    Container runContainer = m.getContainer();
                    if (runContainer.equals(c) || (runContainer.isWorkbookOrTab() && c.equals(runContainer.getParent())))
                    {
                        total++;
                    }
                }

                items.add(new SingleNavItem(this, ss.getName(), total.toString(), new DetailsURL(nav.getBrowseUrl(c, u)), "Samples"));
            }
        }

        return items;
    }

    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(c, u, true))
        {
            UserSchema us = QueryService.get().getUserSchema(u, c, "Samples");
            if (us != null)
            {
                TableInfo ti = us.getTable(ss.getName());
                if (ti != null)
                {
                    ColumnInfo ci = getSubjectColumn(ti);
                    if (ci != null)
                    {
                        QueryCountNavItem qc = new QueryCountNavItem(this, "Samples", ss.getName(), "Samples", ss.getName());
                        qc.setFilter(new SimpleFilter(FieldKey.fromString(ci.getName()), subjectId));
                        items.add(qc);
                    }
                }
            }
        }

        return items;
    }
}
