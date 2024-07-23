import React, { useEffect, useState } from 'react';

import { JBrowseLinearGenomeView, ViewModel } from '@jbrowse/react-linear-genome-view';
import { createTheme } from '@mui/material/styles';
import LogSession from './plugins/LogSession/index';
import ExtendedVariantPlugin from './plugins/ExtendedVariantPlugin/index';
import '../jbrowse.css';
import JBrowseFooter from './components/JBrowseFooter';
import { fetchSession } from '../utils';
import JBrowseFilterPanel from './components/JBrowseFilterPanel';
import { ErrorBoundary } from '../VariantSearch/components/ErrorBoundary';
import { createJBrowseTheme } from '@jbrowse/core/ui';
import { readConfObject } from '@jbrowse/core/configuration';
import { ThemeProvider } from '@mui/material';

const nativePlugins = [ExtendedVariantPlugin, LogSession]
const refTheme = createTheme()

function View(){
    const queryParam = new URLSearchParams(window.location.search);
    const session = queryParam.get('session') || queryParam.get('database')

    const [state, setState] = useState(null);
    const [bgColor, setBgColor] = useState(null)
    useEffect(() => {
        let activeTracks = []
        if (queryParam.get('activeTracks')) {
            activeTracks = activeTracks.concat(queryParam.get('activeTracks').split(','))
        }

        if (queryParam.get('tracks')) {
            activeTracks = activeTracks.concat(queryParam.get('tracks').split(','))
        }

        fetchSession(queryParam, session, nativePlugins, refTheme, setState, false, activeTracks, setBgColor)
    }, [])

    if (session === null){
        return (<p>Error - no session provided.</p>)
    }
    else if (state === null){
        return (<p>Loading...</p>)
    }
    else if (state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }

    // @ts-ignore
    const theme = createJBrowseTheme(readConfObject(state.config.configuration, 'theme'))

    return (
        //TODO: can we make this expand to full page height?
        <div style={{height: "100%"}}>
            <ErrorBoundary>
                <ThemeProvider theme={theme}>
                    <JBrowseLinearGenomeView viewState={state as ViewModel} />
                    <JBrowseFooter viewState={state} bgColor={bgColor}/>
                    <JBrowseFilterPanel session={state.session} />
                </ThemeProvider>
            </ErrorBoundary>
        </div>
    )
}

export default View

