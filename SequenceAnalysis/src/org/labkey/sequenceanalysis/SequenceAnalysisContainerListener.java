/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.sequenceanalysis;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;

public class SequenceAnalysisContainerListener implements ContainerListener
{
    public void containerCreated(Container c, User user)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        SequenceAnalysisManager.get().deleteContainer(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {        
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}