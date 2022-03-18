import React, { useState } from 'react'

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