import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { createTheme } from '@mui/material/styles';
import './search.css';

import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';
import { fetchSession, navigateToBrowser } from '../utils';
import StandaloneSearchComponent from './components/StandaloneSearchComponent';

const nativePlugins = [ExtendedVariantPlugin, LogSession]

const StandaloneSearch = observer(({ sessionId, selectedRegion }: { sessionId: any, selectedRegion?: string}) => {
    if (!sessionId){
        return(<p>No session Id provided. Please have your site admin use the customize icon to set the session ID for this webpart.</p>)
    }

    const queryParam = new URLSearchParams(window.location.search)
    const refTheme = createTheme()
    const [state, setState] = useState(null)

    // Get the LinearGenomeViewModel from the API, providing the session as a parameter
    useEffect(() => {
        fetchSession(queryParam, sessionId, nativePlugins, refTheme, setState, false)
    }, []);

    // Error handle and then render the component
    if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }

    const { session } = state
    const assemblyName = state.config.assembly.name

    const handleSearch = (locString) => {
        if (locString) {
            navigateToBrowser(sessionId, locString)
        }
    }

    return (
        <StandaloneSearchComponent session={session} onSelect={handleSearch} assemblyName={assemblyName} selectedRegion={selectedRegion}/>
    )
})

export default StandaloneSearch
