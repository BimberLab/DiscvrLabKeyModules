import RefNameAutocompleteWrapper from "./RefNameAutocompleteWrapper"
import React from 'react';

function Search(){
    // Grab session + location information from URL params
    const queryParam = new URLSearchParams(window.location.search);
    const sessionId = queryParam.get('session')

    if (sessionId === null){
        return(<p>Error - no session ID provided.</p>)
    }

    return (
        <RefNameAutocompleteWrapper sessionId={sessionId}/>
    )
}

export default Search

