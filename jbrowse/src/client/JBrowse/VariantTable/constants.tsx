import { GridColumns, GridRenderCellParams, GridValueFormatterParams } from '@mui/x-data-grid';
import { getGenotypeURL } from '../utils';
import React from 'react';

// TODO: we need to look into this for all numeric fields. Because some attributes are per-allele,
// The value of these attributes is a CSV string. See comments in dataUtils/rawFeatureToRow()
// const csvNumberFormatFn = (params: GridValueFormatterParams) => {
//   if (!params.value) {
//     return params.value
//   }
//
//   const strVal = String(params.value)
//   const vals = strVal.includes(",") ? strVal.split(',') : [strVal]
//   return vals.map(val => {
//     console.log(val)
//     val ? Number(val).toLocaleString(navigator.language, {maximumFractionDigits: 6}) : val
//   }).join(', ')
// }

// Columns to be shown, minus the ID column.
export const columns: GridColumns = [
  { field: 'chrom', headerName: 'Chromosome', width: 150, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'pos', headerName: 'Position', width: 150, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'ref', headerName: 'Reference', width: 150, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'alt', headerName: 'Alternative Allele', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'af', headerName: 'Allele Frequency', width: 50, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'variant_type', headerName: 'Type', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'impact', headerName: 'Impact', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'overlapping_genes', headerName: 'Overlapping Genes', type: "string", flex: 1, headerAlign: 'left' },
  { field: 'cadd_ph', headerName: 'CADD Score', width: 50, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'track_id', headerName: 'Track ID', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true },
  { field: 'start', headerName: 'Start Location', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true },
  { field: 'end', headerName: 'End Location', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true },
  {
    field: 'show_genotypes',
    headerName: 'Genotypes',
    width: 50,
    flex: 1,
    headerAlign: 'left',
    renderCell: (params: GridRenderCellParams) => {
      return (<a target="_blank" href={getGenotypeURL(params.row.trackId, params.row.chrom, params.row.start, params.row.end)}>View Genotypes</a>)
    }
  },
]

// Default state of each filter.
export const defaultFilters = {
  ref: '',
  alt: '',
  impact: '',
  overlapping_genes: '',
  variant_type: '',
}