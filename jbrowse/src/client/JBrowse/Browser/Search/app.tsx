import React from 'react';
import ReactDOM from 'react-dom';

import Search from '../Search'
import { Ajax, Utils, ActionURL } from '@labkey/api'

const queryParam = new URLSearchParams(window.location.search);
const session = queryParam.get('session')


// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Search />, document.getElementById('app'))
});
