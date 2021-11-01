import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import Search from '../Search';

App.registerApp<any>('jbrowseSearchWebpart', target => {
    ReactDOM.render(<Search />, document.getElementById(target));
});
