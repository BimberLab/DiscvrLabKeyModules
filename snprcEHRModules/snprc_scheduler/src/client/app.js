/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 3 2018      
    ==================================================================================
*/

// import React
import * as React from 'react';
import ReactDOM from 'react-dom'

// import Redux and our store
import { Provider } from 'react-redux';
import initializeStore from './store/initializeStore'

// import routing
import UIRouter from './routers/UIRouter';

// build the store container
const store = initializeStore();
const appContainer = (<Provider store={store}><UIRouter /></Provider>);

let hasRendered = false;
const render = () => {
    if (!hasRendered) {
        console.log("app rendering...");
        ReactDOM.render(appContainer, document.getElementById('app'));
        hasRendered = true;
        console.log("app render complete!");
    }
};

render();

