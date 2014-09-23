/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.sla.security;

import org.labkey.api.ehr.security.AbstractEHRPermission;

/**
 * User: bimber
 * Date: 1/17/13
 * Time: 7:49 PM
 */
public class SLAEntryPermission extends AbstractEHRPermission
{
    public SLAEntryPermission()
    {
        super("SLAEntryPermission", "This is the base permission used toggle whether the SLA forms are visible");
    }
}
