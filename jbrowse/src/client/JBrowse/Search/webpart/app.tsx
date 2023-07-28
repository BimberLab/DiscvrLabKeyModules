import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import StandaloneSearch from '../StandaloneSearch';

App.registerApp<any>('jbrowseSearchWebpart', (target: string, sessionId: string) => {
    ReactDOM.render(
        <StandaloneSearch sessionId={sessionId} />
    , document.getElementById(target));
});
