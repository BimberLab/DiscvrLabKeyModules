import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getEnv } from 'mobx-state-tree';
import { createTheme } from '@mui/material/styles';
import { parseLocString } from '@jbrowse/core/util';
import { readConfObject } from '@jbrowse/core/configuration';
import { createJBrowseTheme } from '@jbrowse/core/ui';
import { ThemeProvider } from '@mui/material';
import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';
import VariantTableWidget from './components/VariantTableWidget';
import { fetchSession } from '../utils';
import { ErrorBoundary } from './components/ErrorBoundary';
import LoadingIndicator from './components/LoadingIndicator';
import deepmerge from '@mui/utils/deepmerge';

const nativePlugins = [ExtendedVariantPlugin, LogSession]

function VariantTable() {
    const queryParam = new URLSearchParams(window.location.search);
    const sessionId = queryParam.get('session') || queryParam.get('database') || queryParam.get('sessionId')
    const locString = queryParam.get('location') || queryParam.get('loc')
    const refTheme = createTheme()

    const themeAdditions = {
        components: {
            MuiTooltip: {
                styleOverrides: {
                    tooltip: {
                        fontSize: '0.8rem',
                    },
                },
            },
        },
    };

    if (!sessionId){
        return(<p>No session Id provided.</p>)
    }

    const trackId = queryParam.get('trackId')
    if (!trackId) {
        return(<p>Must provide the track Id</p>)
    }

    const [session, setSession] = useState(null)
    const [state, setState] = useState(null)
    const [theme, setTheme] = useState(createTheme())
    const [view, setView] = useState(null)
    const [parsedLocString, setParsedLocString] = useState(null)
    const [assemblyNames, setAssemblyNames] = useState(null)
    const [pluginManager, setPluginManager] = useState(null)
    const [rpcManager, setRpcManager] = useState(null)
    const [assembly, setAssembly] = useState(null)


    // Get the LinearGenomeViewModel from the API, providing the session as a parameter
    useEffect(() => {
        async function successCallback(state) {
            const { session } = state
            const { pluginManager } = getEnv(state)
            const { view } = session
            const { assemblyNames, assemblyManager, rpcManager } = session
            setAssembly(await assemblyManager.waitForAssembly(assemblyNames[0]))
            setRpcManager(rpcManager)
            setSession(session)
            setAssemblyNames(assemblyNames)
            setView(view)
            setPluginManager(pluginManager)

            const isValidRefNameForAssembly = function(refName: string, assemblyName?: string) {
                return assemblyManager.isValidRefName(refName, assemblyNames[0])
            }

            if (locString) {
                const parsedLocString = parseLocString(locString, isValidRefNameForAssembly)
                setParsedLocString(parsedLocString)
            }

            setState(state)
            // @ts-ignore
            setTheme(createTheme(deepmerge(createJBrowseTheme(readConfObject(state.config.configuration, 'theme')), themeAdditions)))
        }

        // NOTE: pass trackId for activeTracks, to ensure view.tracks contains it
        fetchSession(queryParam, sessionId, nativePlugins, refTheme, setState, true, [trackId], undefined, successCallback, trackId)
    }, []);

    return (
        <ThemeProvider theme={theme}>
        <div style={{height: "80vh", display:"block"}}>
            {/* @ts-ignore -- FIXME: Build is resolving the wrong @types/react */}
            <ErrorBoundary>
                <VariantTableWidget assembly={assembly} trackId={trackId} parsedLocString={parsedLocString} sessionId={sessionId} session={session} pluginManager={pluginManager}/>
            </ErrorBoundary>
        </div>
        </ThemeProvider>
    )
}

export default observer(VariantTable)
