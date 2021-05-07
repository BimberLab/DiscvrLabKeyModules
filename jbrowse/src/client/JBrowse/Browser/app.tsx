import React from 'react';
import ReactDOM from 'react-dom';

import View from './Browser';
require("regenerator-runtime/runtime")
// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<View />, document.getElementById('app'));
});


/*
// in pages/index.js
import React, {lazy} from 'react'
//import dynamic from 'next/dynamic'

const Browser = lazy(() => import("./Browser"));
//const Browser = dynamic(() => import('./Browser'), { ssr: false })

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Browser />, document.getElementById('app'));
});*/