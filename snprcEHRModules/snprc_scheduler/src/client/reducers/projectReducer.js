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

import { ANIMAL_LIST_RECEIVED, 
         ANIMAL_LIST_REQUEST_FAILED, 
         PROJECT_LIST_RECEIVED, 
         PROJECT_LIST_REQUEST_FAILED,
         PROJECT_SELECTED,
         PROJECT_LIST_FILTERED,
         ANIMAL_LIST_FILTERED,
         TIMELINE_LIST_RECEIVED
       } from "../actions/dataActions";

export default (state = { }, action) => {  
    let nextState = Object.assign({ }, state);
    let value = '';
    nextState.errors = [];
    switch (action.type) { 
        case PROJECT_LIST_RECEIVED:
            // action payload is the project array
            nextState.allProjects = action.payload;
            nextState.projects = action.payload;
            break;
        case ANIMAL_LIST_RECEIVED:
            // action payload is the animal array
            nextState.allAnimals = action.payload;
            nextState.animals = action.payload;
            break;
        case PROJECT_LIST_REQUEST_FAILED:
            // action payload is the exception
            nextState.projects = [];
            nextState.errors.push(action.payload);
            break;
        case ANIMAL_LIST_REQUEST_FAILED:
            // action payload is the exception
            nextState.animals = [];
            nextState.errors.push(action.payload);
            break;
        case PROJECT_SELECTED:
            // action payload is the selected project ID
            nextState.projects.forEach((p) => {
                if (p.ProjectId.value == action.payload) nextState.selectedProject = p;
            })
            break;
        case PROJECT_LIST_FILTERED:
            // action payload is the filter value
            value = (action.payload + '').toUpperCase();
            if (value != '') {
                nextState.projects = [];
                nextState.allProjects.forEach((p) => {
                    if (p.Description.value.toString().toUpperCase().indexOf(value) > -1 || 
                        p.ProjectId.value.toString().toUpperCase().indexOf(value) > -1  ||    
                        p.ChargeId.value.toString().toUpperCase().indexOf(value) > -1  ||
                        p.Iacuc.value.toString().toUpperCase().indexOf(value) > -1  ||
                        p.RevisionNum.value.toString().toUpperCase().indexOf(value) > -1  ||
                        p.StartDate.value.toString().toUpperCase().indexOf(value) > -1  ||
                        p.EndDate.value.toString().toUpperCase().indexOf(value) > -1) 
                    { nextState.projects.push(p); }
                });               
            } else nextState.projects = nextState.allProjects;
            break;
        case ANIMAL_LIST_FILTERED:
            // action payload is the filter value
            value = (action.payload + '').toUpperCase();
            if (value != '') {
                nextState.animals = [];
                nextState.allAnimals.forEach((a) => {
                    if (a.Id.value.toString().toUpperCase().indexOf(value) > -1 || 
                        a.Age.value.toString().toUpperCase().indexOf(value) > -1 || 
                        a.Gender.displayValue.toString().toUpperCase().indexOf(value) > -1) {
                        nextState.animals.push(a);
                    }
                })
            } else nextState.animals = nextState.allAnimals;
            break;
        case TIMELINE_LIST_RECEIVED:
            // action payload is the timeline array
            nextState.allTimelines = action.payload;
            nextState.timelines = action.payload;
            break;
    };
    if (verboseOutput) {
        console.log('projectReducer() -> ' + action.type);
        console.log(nextState);  
    }     
    return nextState;
};
