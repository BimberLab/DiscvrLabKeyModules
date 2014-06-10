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
package org.labkey.biotrust.view;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

/**
 * User: cnathe
 * Date: 2/24/13
 */
public class BioTrustPendingSampleRequestsWebPart extends BaseWebPartFactory
{
    public static final String NAME = "NW BioTrust Pending Requests";

    public BioTrustPendingSampleRequestsWebPart()
    {
        super(NAME, WebPartFactory.LOCATION_BODY);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/biotrust/view/biotrustPendingSampleRequests.jsp", webPart);
        view.setTitle("Pending Sample Requests");
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}
