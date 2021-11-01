import React from 'react';
import ReactDOM from 'react-dom';
import Search from './Search';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Search />, document.getElementById('app'))
});
