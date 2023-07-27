import React, { useState } from 'react';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { navigateToBrowser, navigateToTable } from '../../utils';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { fetchResults } from '@jbrowse/plugin-linear-genome-view/esm/LinearGenomeView/components/util';

export default function StandaloneSearchComponent(props: {session: any, trackId: string, selectedRegion: string, assemblyName: string, tableUrl: boolean}) {
    const { session, trackId, selectedRegion, assemblyName, tableUrl } = props
    const [op, setOption] = useState<BaseResult | undefined>()
    const { view } = session
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    if (op && !tableUrl) {
        navigateToBrowser(view.id, op.getLocation())
    } else if (op && tableUrl) {
        navigateToTable(view.id, op.getLocation(), trackId)
    }

    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)
    const effectiveSelectedRegion = op?.getLocation() || selectedRegion

    const doFetch = (queryString: string) => {
        return fetchResults( { queryString, assembly, textSearchManager, rankSearchResults, searchScope })
    }

    console.log(assemblyName)
    return (
        <span style={tableUrl ? {display: 'inline-block', marginRight: '14px'} : {}}>
      <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={doFetch}
          value={effectiveSelectedRegion}
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
}