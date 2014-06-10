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
import org.labkey.api.view.ActionURL;

/**
 * User: klum
 * Date: 3/11/13
 */
public abstract class ReviewEmailTemplate extends BioTrustEmailTemplate
{
    public static final String BODY = "A NWBioTrust sample request of type: ^sampleType^ has been marked for ^status^ by ^submittedByEmail^.\n\n" +
            "Comments from RC:\n\n^rcComments^\n\n" +
            "To view the sample request, use the following link:\n\n^sampleLink^";

    private ActionURL _sampleLink;
    private String _sampleType;
    private String _status;
    private String _rcComments;

    public ReviewEmailTemplate(String name, String subject, String description)
    {
        super(name, subject, BODY, description);

        _replacements.add(new ReplacementParam<String>("sampleType", String.class, "The type of the sample designated for review"){
            @Override
            public String getValue(Container c)
            {
                return null == _sampleType ? "" : _sampleType;
            }
        });
        _replacements.add(new ReplacementParam<String>("sampleLink", String.class, "A link to the samples marked for review"){
            @Override
            public String getValue(Container c)
            {
                return null == _sampleLink ? "" : _sampleLink.getURIString();
            }
        });
        _replacements.add(new ReplacementParam<String>("status", String.class, "The sample request status")
        {
            @Override
            public String getValue(Container c)
            {
                return null == _status ? "" : _status;
            }
        });
        _replacements.add(new ReplacementParam<String>("rcComments", String.class, "The comments from the RC for the Approvers"){
            @Override
            public String getValue(Container c)
            {
                return null == _rcComments ? "" : _rcComments;
            }
        });
    }

    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }

    public ActionURL getSampleLink()
    {
        return _sampleLink;
    }

    public void setSampleLink(ActionURL sampleLink)
    {
        _sampleLink = sampleLink;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public String getRcComments()
    {
        return _rcComments;
    }

    public void setRcComments(String rcComments)
    {
        _rcComments = rcComments;
    }
}
