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

const verboseOutput = false;

import { ANIMAL_LIST_REQUESTED, 
         ANIMAL_LIST_RECEIVED, 
         ANIMAL_LIST_REQUEST_FAILED, 
         PROJECT_LIST_REQUESTED, 
         PROJECT_LIST_RECEIVED, 
         PROJECT_LIST_REQUEST_FAILED,
         PROJECT_SELECTED,
         PROJECT_LIST_FILTERED
       } from "../actions/dataActions";

export default (state = { }, action) => {  
    let nextState = Object.assign({ }, state);
    switch (action.type) { 
        case PROJECT_LIST_RECEIVED:
            // action payload is the project array
            nextState.projects = action.payload;
            break;
        case PROJECT_SELECTED:
            // action payload is the selected project ID
            nextState.projects.forEach((p) => {
                if (p.ProjectId.value == action.payload) nextState.selectedProject = p;
            })
            break;
    };
    if (verboseOutput) {
        console.log('projectReducer() -> ' + action.type);
        console.log(nextState);  
    }     
    return nextState;
};
