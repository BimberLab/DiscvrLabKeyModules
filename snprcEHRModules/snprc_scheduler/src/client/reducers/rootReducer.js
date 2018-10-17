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

export default (state = {}, action) => {
    if (verboseOutput) console.log('rootReducer -> ' + action.type);
    switch (action.type) {

        default: return { ...state, payload: { }} ;
    };
};