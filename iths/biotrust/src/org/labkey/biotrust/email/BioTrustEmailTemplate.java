/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust.email;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.emailTemplate.EmailTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 3/9/13
 */
public abstract class BioTrustEmailTemplate extends EmailTemplate
{
    protected List<ReplacementParam> _replacements = new ArrayList<>();
    private User _submittedBy;

    public BioTrustEmailTemplate(String name)
    {
        super(name);
        commonConstruct();
    }

    public BioTrustEmailTemplate(String name, String subject, String body, String description)
    {
        super(name, subject, body, description);
        commonConstruct();
    }

    private void commonConstruct()
    {
        setPriority(10);
        _replacements.addAll(super.getValidReplacements());

        _replacements.add(new ReplacementParam<String>("submittedByEmail", String.class, "The email address of the user who submitted the email."){
                @Override
                public String getValue(Container c)
                {
                    return null == _submittedBy ? "" : _submittedBy.getEmail();
                }
            });
        _replacements.add(new ReplacementParam<String>("submittedByDisplayName", String.class, "The display name of the user who submitted the email."){
                @Override
                public String getValue(Container c)
                {
                    return null == _submittedBy ? "" : _submittedBy.getDisplayName(null);
                }
            });
    }

    @Override
    public List<ReplacementParam> getValidReplacements()
    {
        return _replacements;
    }

    public User getSubmittedBy()
    {
        return _submittedBy;
    }

    public void setSubmittedBy(User submittedBy)
    {
        _submittedBy = submittedBy;
    }
}
