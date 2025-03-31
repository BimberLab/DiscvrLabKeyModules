import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import StandaloneSearch from '../StandaloneSearch';

App.registerApp<any>('jbrowseSearchWebpart', (target: string, sessionId: string) => {
    createRoot(document.getElementById(target)).render(<StandaloneSearch sessionId={sessionId} />)
}, true);