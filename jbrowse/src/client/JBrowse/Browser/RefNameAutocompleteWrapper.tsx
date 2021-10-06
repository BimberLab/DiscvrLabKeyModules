import React, { useState } from 'react'
import { observer } from 'mobx-react'
import { getSession } from '@jbrowse/core/util'
import { RefNameAutocomplete } from '@jbrowse/plugin-linear-genome-view'
import { ViewModel } from '@jbrowse/react-linear-genome-view'
import BaseResult, { RefSequenceResult }from '@jbrowse/core/TextSearch/BaseResults'

const RefNameAutocompleteWrapper = observer(({ viewState }: { viewState: ViewModel }) => {
  function navigate() {
    window.location.href = "/home/jbrowse-jbrowse.view?session=" + op.getTrackId() + "&location=" + op.getLocation();
  }

  const { session } = viewState
  const { view } = session

  const { assemblyNames, assemblyManager } = getSession(view)

  const [selectedAsm, setSelectedAsm] = useState(assemblyNames[0])
  const [op, setOption] = useState<BaseResult | undefined>()

  const assembly = assemblyManager.get(selectedAsm)
  const regions = assembly?.regions || []

  const selectedRegion = op?.getLocation()
  const message = !assemblyNames.length ? 'No configured assemblies' : ''

  return (
    <span>
      <RefNameAutocomplete
        model={view}
        assemblyName={message ? undefined : selectedAsm}
        value={selectedRegion}
        onSelect={option => {
          setOption(option)
        }}
        TextFieldProps={{
          margin: 'normal',
          variant: 'outlined',
          helperText: 'Enter a sequence or location',
        }}
      />

      <button onClick={navigate}>
          Search
      </button>
    </span>
  )
})

export default RefNameAutocompleteWrapper
