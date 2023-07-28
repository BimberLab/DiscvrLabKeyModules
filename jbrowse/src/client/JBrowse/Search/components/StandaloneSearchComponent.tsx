import React, { useState } from 'react';
import BaseResult from '@jbrowse/core/TextSearch/BaseResults';
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view';
import { fetchResults } from '@jbrowse/plugin-linear-genome-view/esm/LinearGenomeView/components/util';

export default function StandaloneSearchComponent(props: { session: any, selectedRegion: string, assemblyName: string, onSelect: (string) => void, forVariantTable?: boolean}) {
    const { session, selectedRegion, assemblyName, onSelect, forVariantTable } = props

    const { view } = session
    const { textSearchManager, assemblyManager } = session
    const { rankSearchResults } = view

    const assembly = assemblyManager.get(assemblyName)
    const searchScope = view.searchScope(assemblyName)

    const doFetch = (queryString: string) => {
        return fetchResults( { queryString, assembly, textSearchManager, rankSearchResults, searchScope })
    }

    return (
        <span style={forVariantTable ? {display: 'inline-block', marginRight: '14px'} : {}}>
        <RefNameAutocomplete
          model={view}
          assemblyName={assemblyName ?? undefined}
          fetchResults={doFetch}
          value={selectedRegion}
          onSelect={option => {
              if (option.getLocation()) {
                  onSelect(option?.getLocation())
              }
              else if (option.results) {
                  let contigs = []
                  let minVal = -1
                  let maxVal = -1

                  option.results.forEach(r => {
                      if (r.getLocation()) {
                          const l = r.getLocation().split(":")
                          if (l.length !== 2) {
                              console.error("Invalid location: " + r.getLocation())
                              return false
                          }

                          const [ start, end ] = l[1].split("..")
                          if (isNaN(Number(start)) || isNaN(Number(end))) {
                              console.error("Invalid location: " + r.getLocation())
                              return false
                          }

                          contigs.push(l[0])
                          if (minVal == -1 || minVal > Number(start)) {
                              minVal = Number(start)
                          }

                          if (maxVal == -1 || maxVal < Number(end)) {
                              maxVal = Number(end)
                          }
                      }
                  })

                  if (contigs.length !== 1) {
                      console.error("Invalid location after parse")
                      return null
                  }
                  else {
                      return(contigs[0] + ":" + minVal + "-" + maxVal)
                  }
              }
              else {
                  console.error('No location found for search result')
              }
          }}
          TextFieldProps={{
              margin: 'normal',
              variant: 'outlined',
              helperText: forVariantTable ? undefined : 'Enter a gene or location',
              style: { margin: 7, minWidth: '175px' },
              InputProps: {
                  style: {
                      paddingBottom: 0,
                      height: forVariantTable ? 45 : 32
                  }
              }
          }}
      />
    </span>
    )
}