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
package org.labkey.vbdsearch.email;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: 4/29/13
 */
public class RepositoryContactEmailTemplate extends EmailTemplate
{
    public static final String NAME = "VBD Repository Contact";
    public static final String SUBJECT = "Contact from VBD";
    public static final String DESCRIPTION = "The email sent to the repository from VBD search.";
    public static final String BODY = "We are interested in the following types of specimens found on the VBD web site:\n\n" +
            "^filters^\n\n" +
            "Comments and additional instructions : '^comments^'.\n\n" +
            "To view the sample records associated with this request, use the following link: ^sampleLink^";

    private ActionURL _sampleLink;
    private String _comments;
    private String _filters;
    protected List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();

    public RepositoryContactEmailTemplate()
    {
        super(NAME, SUBJECT, BODY, DESCRIPTION);
        commonConstruct();
    }

    private void commonConstruct()
    {
        setPriority(10);
        _replacements.addAll(super.getValidReplacements());

        _replacements.add(new ReplacementParam<String>("filters", String.class, "The description of the filters used to generate this specimen search."){
                @Override
                public String getValue(Container c)
                {
                    return null == _filters ? "no filters." : _filters;
                }
            });
        _replacements.add(new ReplacementParam<String>("comments", String.class, "Comments or additional instructions."){
                @Override
                public String getValue(Container c)
                {
                    return null == _comments ? "(no comments or additional instructions)" : _comments;
                }
            });
        _replacements.add(new ReplacementParam<String>("sampleLink", String.class, "The URL to the specimen records."){
                @Override
                public String getValue(Container c)
                {
                    return null == _sampleLink ? "" : _sampleLink.getURIString();
                }
            });
    }

    @Override
    public List<ReplacementParam> getValidReplacements()
    {
        return _replacements;
    }

    public ActionURL getSampleLink()
    {
        return _sampleLink;
    }

    public void setSampleLink(ActionURL sampleLink)
    {
        _sampleLink = sampleLink;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getFilters()
    {
        return _filters;
    }

    public void setFilters(String filters)
    {
        _filters = filters;
    }
}
