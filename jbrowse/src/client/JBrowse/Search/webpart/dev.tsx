import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import StandaloneSearch from '../StandaloneSearch';

App.registerApp<any>('jbrowseSearchWebpart', (target: string, sessionId: string) => {
    ReactDOM.render(
        <AppContainer>
            <StandaloneSearch sessionId={sessionId} />
        </AppContainer>,
        document.getElementById(target)
    );
}, true /* hot */);

declare const module: any;
