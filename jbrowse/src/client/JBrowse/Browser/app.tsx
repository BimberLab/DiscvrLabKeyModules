import React from 'react';
import ReactDOM from 'react-dom';

import View from './Browser';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<View />, document.getElementById('app'))
});
