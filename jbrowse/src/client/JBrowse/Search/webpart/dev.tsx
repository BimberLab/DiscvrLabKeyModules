import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import Search from '../Search';

App.registerApp<any>('jbrowseSearchWebpart', (target: string) => {
    ReactDOM.render(
        <AppContainer>
            <Search />
        </AppContainer>,
        document.getElementById(target)
    );
}, true /* hot */);

declare const module: any;
