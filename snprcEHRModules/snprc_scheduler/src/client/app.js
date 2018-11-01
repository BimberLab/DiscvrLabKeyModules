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

// import the application styles
import './styles/Default.style.css'

// import routing
import UIRouter from './routers/UIRouter';
import { createAction, fetchProjects } from './actions/dataActions';

// build the store and container
const store = initializeStore();
const appContainer = (<Provider store={store}><UIRouter store={store} /></Provider>);

let hasRendered = false;
let debugUI = false;

const render = () => {
    if (!hasRendered) {
        if (debugUI) console.log("React application rendering...");
        ReactDOM.render(appContainer, document.getElementById('app'));
        hasRendered = true;
        if (debugUI) console.log("Application render complete!");
    }
};

// ask the store to get the current projects
store.dispatch(fetchProjects());

// render the application
render();


