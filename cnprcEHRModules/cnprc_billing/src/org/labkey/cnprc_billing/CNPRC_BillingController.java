/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.cnprc_billing;

import org.labkey.api.action.SpringActionController;

public class CNPRC_BillingController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(CNPRC_BillingController.class);
    public static final String NAME = "cnprc_billing";

    public CNPRC_BillingController()
    {
        setActionResolver(_actionResolver);
    }

}