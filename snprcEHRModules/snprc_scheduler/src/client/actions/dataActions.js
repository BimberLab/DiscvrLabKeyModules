/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 9 2018      
    ==================================================================================
*/

export const PROJECT_LIST_REQUESTED = 'PROJECT_LIST_REQUESTED';
export const PROJECT_LIST_RECEIVED = 'PROJECT_LIST_RECEIVED';
export const PROJECT_LIST_REQUEST_FAILED = 'PROJECT_LIST_REQUEST_FAILED';
export const ANIMAL_LIST_REQUESTED = 'ANIMAL_LIST_REQUESTED';
export const ANIMAL_LIST_RECEIVED = 'ANIMAL_LIST_RECEIVED';
export const ANIMAL_LIST_REQUEST_FAILED = 'ANIMAL_LIST_REQUEST_FAILED';
export const PROJECT_SELECTED = 'PROJECT_SELECTED';
export const PROJECT_LIST_FILTERED = 'PROJECT_LIST_FILTERED';
export const NO_OP = 'NO_OP';

export function createAction(type, payload) {
    switch(type) {
        case PROJECT_LIST_REQUESTED: return { type: type };
        case PROJECT_LIST_RECEIVED: return { type: type, payload: payload };
        case PROJECT_LIST_REQUEST_FAILED: return { type: type, payload: payload, error: true };
        case ANIMAL_LIST_REQUESTED: return { type: type };
        case ANIMAL_LIST_RECEIVED: return { type: type, payload: payload };
        case ANIMAL_LIST_REQUEST_FAILED: return { type: type, payload: payload, error: true };
        case PROJECT_SELECTED: return { type: type, payload: payload };
        case PROJECT_LIST_FILTERED: return { type: type, payload: payload };
        default: return { type: type }
    }    
}

export function fetchProjects() {
    return (dispatch, getState) => {
        dispatch(createAction(PROJECT_LIST_REQUESTED));
        LABKEY.Query.selectRows({
            queryName: 'ProjectDetails', requiredVersion: 9.1, schemaName: 'snd', filterArray: [], sort: 'ProjectId,RevisionNum',
            columns: 'ProjectId,RevisionNum,ChargeId,Description,StartDate,EndDate,ProjectType,VsNumber,Active,ObjectId,iacuc,veterinarian',
            success: (results) => { dispatch(createAction(PROJECT_LIST_RECEIVED, results.rows)); },
            failure: (error) => { dispatch(createAction(PROJECT_LIST_REQUEST_FAILED, error)) }
        });
    };
}

export function fetchAnimalsByProject(projectId, revision = 0) {
    return (dispatch, getState) => {
        dispatch(createAction(ANIMAL_LIST_REQUESTED));
        LABKEY.Query.selectRows({
            queryName: 'AnimalsByProject', requiredVersion: 9.1, schemaName: 'snd', filterArray: [ 
                LABKEY.Filter.create('ProjectId', projectId.toString(), LABKEY.Filter.Types.EQUAL),
                LABKEY.Filter.create('RevisionNum', revision.toString(), LABKEY.Filter.Types.EQUAL)
            ],           
            columns: 'Id,ProjectId,RevisionNum,StartDate,EndDate,Gender,ChargeId,Iacuc,AssignmentStatus',
            success: (results) => { dispatch(createAction(ANIMAL_LIST_RECEIVED, results.rows)) },
            failure: (error) => { dispatch(createAction(ANIMAL_LIST_REQUEST_FAILED, error)) }
        });
    };
}

export function filterProjects(pattern) {
    return (dispatch) => {
        dispatch(createAction(PROJECT_LIST_FILTERED, pattern));
    }
}

export function selectProject(projectId) {
    return (dispatch, getState) => {
        dispatch(createAction(PROJECT_SELECTED, projectId));
    }
}