import React, { useState } from 'react'
import { getConf } from '@jbrowse/core/configuration'

export function ExportButton({
    onExport,
    children
  }: {
    onExport: () => Promise<unknown>;
    children: React.ReactChild;
  }) {
    const [exporting, setExporting] = useState(false)
    return (
      <button
        style={{marginRight: "4px"}}
        disabled={exporting}
        onClick={async () => {
          setExporting(true)
          await onExport()
          setExporting(false)
        }}
      >
        {exporting ? 'Exporting' : children}
      </button>
    );
  }

export function FilterButton(props) {
  const { setFiltersOn, filtersOn } = props
  return(<button onClick={() => setFiltersOn(!filtersOn)}>{ filtersOn ? "Hide Filters" : "Show Filters"}</button>)
}

export function JBrowseUIButton(props) {
  const onClick = () => {
    const { session, widget, addActiveWidgetId } = props
    session.showWidget(widget)
    addActiveWidgetId(widget.id)
  }

  return(<button onClick={onClick}>{props.children}</button>)
}