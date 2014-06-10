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
package org.labkey.biotrust.security;

import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.biotrust.BioTrustModule;

/**
 * User: klum
 * Date: 1/22/13
 */
public class UpdateReviewPermission extends AbstractPermission
{
    public UpdateReviewPermission()
    {
        super("NWBT Update Review",
                "Can update the review of a specimen request",
                BioTrustModule.class);
    }
}
