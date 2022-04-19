import React, { useEffect, useState } from 'react';
import { observer } from 'mobx-react';
import { getSession } from '@jbrowse/core/util';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { createTheme } from '@material-ui/core/styles';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { ActionURL, Ajax } from '@labkey/api';
import './search.css';
import { SearchType } from '@jbrowse/core/data_adapters/BaseAdapter';

import LogSession from '../Browser/plugins/LogSession/index';
import ExtendedVariantPlugin from '../Browser/plugins/ExtendedVariantPlugin/index';
import { fetchSession } from '../utils'
import { navigateToTable, navigateToBrowser } from '../utils';

const nativePlugins = [ExtendedVariantPlugin, LogSession]

const StandaloneSearch = observer(({ sessionId, tableUrl, trackId, selectedRegion }: { sessionId: any, tableUrl: boolean, trackId?: string, selectedRegion?: string}) => {
    if (!sessionId){
        return(<p>No session Id provided. Please have your site admin use the customize icon to set the session ID for this webpart.</p>)
    }

    const queryParam = new URLSearchParams(window.location.search)
    const refTheme = createTheme()
    const [state, setState] = useState(null)
    const [op, setOption] = useState<BaseResult | undefined>()
    if (op && !tableUrl) {
        navigateToBrowser(sessionId, op.getLocation())
    } else if (op && tableUrl) {
        navigateToTable(sessionId, op.getLocation(), trackId)
    }

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
    const { view } = session
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    const { assemblyNames } = getSession(session)
    if (!assemblyNames.length){
        return (<p>No configured assemblies</p>)
    }

    const assemblyName = assemblyNames[0]
    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)
    selectedRegion = op?.getLocation() || selectedRegion

    // TODO: can we avoid this duplication?
    function dedupe(
        results: BaseResult[] = [],
        cb: (result: BaseResult) => string,
    ) {
        return results.filter(
            (elt, idx, self) => idx === self.findIndex(t => cb(t) === cb(elt)),
        )
    }

    // TODO: can we avoid this duplication?
    async function fetchResults(query: string, searchType?: SearchType) {
        if (!textSearchManager) {
            console.error('No text search manager')
        }

        const textSearchResults = await textSearchManager?.search(
            {
                queryString: query,
                searchType,
            },
            searchScope,
            rankSearchResults,
        )

        const refNameResults = assembly?.allRefNames
            ?.filter(refName => refName.startsWith(query))
            .map(r => new BaseResult({ label: r }))
            .slice(0, 10)

        return dedupe(
            [...(refNameResults || []), ...(textSearchResults || [])],
            elt => elt.getId(),
        )
    }

    return (
    <span style={tableUrl ? {display: 'inline-block', marginRight: '14px'} : {}}>
      <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={fetchResults}
          value={selectedRegion}
          onSelect={option => {
              setOption(option)
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: tableUrl ? undefined : 'Enter a gene or location',
              style: { margin: 7, minWidth: '175px' },
              InputProps: {
                  style: {
                      paddingBottom: 0,
                      height: tableUrl ? 45 : 32
                  }
              }
          }}
      />
    </span>
    )
})

export default StandaloneSearch
