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
const SEARCH_MODE_LABKEY = 1;
const SEARCH_MODE_SND = 2;
var SEARCH_MODE = SEARCH_MODE_SND;


import { ANIMAL_LIST_RECEIVED, 
         ANIMAL_LIST_REQUEST_FAILED, 
         PROJECT_LIST_RECEIVED, 
         PROJECT_LIST_REQUEST_FAILED,
         PROJECT_SELECTED,
         PROJECT_LIST_FILTERED,
         ANIMAL_LIST_FILTERED,
         TIMELINE_LIST_RECEIVED,
         TIMELINE_DUPLICATED
       } from "../actions/dataActions";

function hasValue (source, value)  {
    if (source == null) source = '';
    source = source.value ? source.value : source;
    source = (source + '').toUpperCase();
    if (source.indexOf(value) > -1) return true;
    return false;
};

function cloneTimeline(source) {
    let nt = Object.assign({ timelineId: -1 }, source);
    console.log(nt);
}

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
            nextState.allProjects = [];
            nextState.projects = [];
            nextState.errors.push(action.payload);
            break;
        case ANIMAL_LIST_REQUEST_FAILED:
            // action payload is the exception
            nextState.allAnimals = [];
            nextState.animals = [];
            nextState.errors.push(action.payload);
            break;
        case PROJECT_SELECTED:
            // action payload is the selected project ID
            nextState.projects.forEach((p) => {
                if (SEARCH_MODE == SEARCH_MODE_LABKEY) {
                    if (p.ProjectId.value == action.payload) nextState.selectedProject = p;
                } else if (SEARCH_MODE == SEARCH_MODE_SND) {
                    if (p.projectId.toString() == action.payload.toString()) 
                        nextState.selectedProject = p;
                }  
            })
            break;
        case PROJECT_LIST_FILTERED:
            // action payload is the filter value
            value = (action.payload + '').toUpperCase();
            if (value != '') {
                nextState.projects = [];
                nextState.allProjects.forEach((p) => {
                    switch(SEARCH_MODE) {
                        case SEARCH_MODE_LABKEY:
                            if (p.Description.value.toString().toUpperCase().indexOf(value) > -1 || 
                                p.ProjectId.value.toString().toUpperCase().indexOf(value) > -1  ||    
                                p.ChargeId.value.toString().toUpperCase().indexOf(value) > -1  ||
                                p.Iacuc.value.toString().toUpperCase().indexOf(value) > -1  ||
                                p.RevisionNum.value.toString().toUpperCase().indexOf(value) > -1  ||
                                p.StartDate.value.toString().toUpperCase().indexOf(value) > -1  ||
                                p.EndDate.value.toString().toUpperCase().indexOf(value) > -1) 
                            { nextState.projects.push(p); }
                            break;
                        case SEARCH_MODE_SND:
                            if (hasValue(p.description, value) ||
                                hasValue(p.Iacuc, value) ||
                                hasValue(p.CostAccount, value) ||
                                hasValue(p.referenceId, value) ||
                                hasValue(p.Veterinarian1, value) ||
                                hasValue(p.Veterinarian2, value) ||
                                hasValue(p.VsNumber, value) ||
                                hasValue(p.startDate, value) ||
                                hasValue(p.endDate, value)) 
                            { nextState.projects.push(p); }
                            break;
                    }
                });               
            } else nextState.projects = nextState.allProjects;
            break;
        case ANIMAL_LIST_FILTERED:
            // action payload is the filter value
            value = (action.payload + '').toUpperCase();
            if (value != '') {
                nextState.animals = [];
                nextState.allAnimals.forEach((a) => {
                    if (hasValue(a.Id, value) ||
                        hasValue(a.Age, value) ||
                        hasValue(a.Gender, value))
                    { nextState.animals.push(a); }
                })
            } else nextState.animals = nextState.allAnimals;
            break;
        case TIMELINE_LIST_RECEIVED:
            // action payload is the timeline array
            nextState.allTimelines = action.payload;
            nextState.timelines = action.payload;
            break;
        case TIMELINE_DUPLICATED:

            break;
    };
    if (verboseOutput) {
        console.log('projectReducer() -> ' + action.type);
        console.log(nextState);  
    }     
    return nextState;
};

