import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getEnv, IAnyStateTreeNode } from 'mobx-state-tree';
import { createTheme } from '@mui/material/styles';
import { readConfObject } from '@jbrowse/core/configuration';
import { createJBrowseTheme } from '@jbrowse/core/ui';
import { ThemeProvider } from '@mui/material';
import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';
import VariantTableWidget from './components/VariantTableWidget';
import { fetchSession } from '../utils';
import LoadingIndicator from './components/LoadingIndicator';
import JBrowseFilterPanel from '../Browser/components/JBrowseFilterPanel';
import { ErrorBoundary } from '../VariantSearch/components/ErrorBoundary';
import { Assembly } from '@jbrowse/core/assemblyManager/assembly';

const nativePlugins = [ExtendedVariantPlugin, LogSession];

function VariantTable() {
    const queryParam = new URLSearchParams(window.location.search);
    const sessionId = queryParam.get('session') || queryParam.get('database');
    const locString = queryParam.get('location') || queryParam.get('loc');
    const refTheme = createTheme();

    if (!sessionId) {
        return (<p>No session Id provided.</p>);
    }

    const trackId = queryParam.get('trackId');
    if (!trackId) {
        return (<p>Must provide the track Id</p>);
    }

    const [session, setSession] = useState(null);
    const [state, setState] = useState(null);
    const [theme, setTheme] = useState(null);
    const [view, setView] = useState(null);
    const [pluginManager, setPluginManager] = useState(null);
    const [assembly, setAssembly] = useState<Assembly>(null);
    const [assemblyName, setAssemblyName] = useState<string>(null)


    // Get the LinearGenomeViewModel from the API, providing the session as a parameter
    useEffect(() => {
        async function successCallback(state: IAnyStateTreeNode) {
            const { session } = state
            const { pluginManager } = getEnv(state)
            const { view } = session
            const { assemblyNames, assemblyManager } = session
            setAssembly(await assemblyManager.waitForAssembly(assemblyNames[0]))
            setAssemblyName(assemblyNames[0])
            setSession(session)
            setView(view)
            setPluginManager(pluginManager)
            setState(state)

            // @ts-ignore
            setTheme(createJBrowseTheme(readConfObject(state.config.configuration, 'theme')))
        }

        // NOTE: pass trackId for activeTracks, to ensure view.tracks contains it
        fetchSession(queryParam, sessionId, nativePlugins, refTheme, setState, true, [trackId], undefined, successCallback, trackId)
    }, []);

    // Error handle and then render the component
    if (view === null || theme == null) {
        return (<LoadingIndicator isOpen={true}/>)
    }
    else if (view === "invalid" || state == "invalid") {
        return (<p>Error fetching config. See console for more details</p>)
    }

    if (!assembly) {
        return (<p>No configured assemblies</p>)
    }

    return (
        <ThemeProvider theme={theme}>
            <div style={{height: "80vh", display:"block"}}>
                <ErrorBoundary>
                    <JBrowseFilterPanel session={state.session}/>
                    <VariantTableWidget assembly={assembly} assemblyName={assemblyName} trackId={trackId} locString={locString}
                                        sessionId={sessionId} session={session} pluginManager={pluginManager}/>
                </ErrorBoundary>
            </div>
        </ThemeProvider>
    )
}

export default observer(VariantTable)
