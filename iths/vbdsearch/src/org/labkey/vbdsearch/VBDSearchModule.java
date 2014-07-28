/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.vbdsearch;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.vbdsearch.email.RepositoryContactEmailTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class VBDSearchModule extends DefaultModule
{
    public String getName()
    {
        return "VBDSearch";
    }

    public double getVersion()
    {
        return 14.20;
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected void init()
    {
        addController("vbdsearch", VBDSearchController.class);
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(VBDSearchWebPart.FACTORY);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        //add email templates
        EmailTemplateService ets = EmailTemplateService.get();
        ets.registerTemplate(RepositoryContactEmailTemplate.class);
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}
