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
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;

/**
 * User: cnathe
 * Date: 7/10/13
 */
public class StudyRegisteredEmailTemplate extends BioTrustEmailTemplate
{
    public static final String NAME = "NW BioTrust Study Registered";
    public static final String SUBJECT = "A NWBioTrust Study has been registered";
    public static final String DESCRIPTION = "Sent when a study is registered.";
    public static final String BODY = "A NWBioTrust study has been registered.\n\n" +
            "To view the registered study from your RC dashboard, use the following link:\n\n^sampleRequestLink^";

    private ActionURL _sampleRequestLink;

    public StudyRegisteredEmailTemplate()
    {
        super(NAME, SUBJECT, BODY, DESCRIPTION);

        _replacements.add(new EmailTemplate.ReplacementParam<String>("sampleRequestLink", String.class, "A link to the RC folder"){
            @Override
            public String getValue(Container c)
            {
                return null == _sampleRequestLink ? "" : _sampleRequestLink.getURIString();
            }
        });
    }

    public ActionURL getSampleRequestLink()
    {
        return _sampleRequestLink;
    }

    public void setSampleRequestLink(ActionURL sampleRequestLink)
    {
        _sampleRequestLink = sampleRequestLink;
    }
}