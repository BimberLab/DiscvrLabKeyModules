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
 * Date: 3/18/13
 */
public class SampleReviewerResponseEmailTemplate extends BioTrustEmailTemplate
{
    public static final String NAME = "NW BioTrust Sample Review Response";
    public static final String SUBJECT = "A NWBioTrust Sample request has been updated";
    public static final String DESCRIPTION = "Sent when a sample review response has been submitted by an approver.";
    public static final String BODY = "A NWBioTrust sample request has been updated by ^submittedByEmail^.\n\n" +
            "This request for sample(s) of type : ^sampleType^ has been modified to a new status of : ^reviewStatus^. " +
            "The reviewer has added the following comments : '^reviewComments^'.\n\n" +
            "To view the status of this and other sample requests from your RC dashboard, use the following link: ^sampleLink^";

    private ActionURL _sampleLink;
    private String _sampleType;
    private String _reviewStatus;
    private String _reviewComments;

    public SampleReviewerResponseEmailTemplate()
    {
        super(NAME, SUBJECT, BODY, DESCRIPTION);

        _replacements.add(new ReplacementParam<String>("sampleLink", String.class, "A link to the open studies and sample requests"){
            @Override
            public String getValue(Container c)
            {
                return null == _sampleLink ? "" : _sampleLink.getURIString();
            }
        });
        _replacements.add(new ReplacementParam<String>("sampleType", String.class, "The type of sample request"){
            @Override
            public String getValue(Container c)
            {
                return null == _sampleType ? "" : _sampleType;
            }
        });
        _replacements.add(new ReplacementParam<String>("reviewStatus", String.class, "The updated status of the sample request"){
            @Override
            public String getValue(Container c)
            {
                return null == _reviewStatus ? "" : _reviewStatus;
            }
        });
        _replacements.add(new ReplacementParam<String>("reviewComments", String.class, "Additional comments from the reviewer"){
            @Override
            public String getValue(Container c)
            {
                return null == _reviewComments ? "" : _reviewComments;
            }
        });
    }

    public ActionURL getSampleLink()
    {
        return _sampleLink;
    }

    public void setSampleLink(ActionURL sampleLink)
    {
        _sampleLink = sampleLink;
    }

    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }

    public String getReviewStatus()
    {
        return _reviewStatus;
    }

    public void setReviewStatus(String reviewStatus)
    {
        _reviewStatus = reviewStatus;
    }

    public String getReviewComments()
    {
        return _reviewComments;
    }

    public void setReviewComments(String reviewComments)
    {
        _reviewComments = reviewComments;
    }
}
