import { GridColumns } from '@mui/x-data-grid';

// Columns to be shown, minus the ID column.
export const columns: GridColumns = [
  { field: 'chrom', headerName: 'Chromosome', width: 150, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'pos', headerName: 'Position', width: 150, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'ref', headerName: 'Reference', width: 150, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'alt', headerName: 'Alternative Allele', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'af', headerName: 'Allele Frequency', width: 50, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'variant_type', headerName: 'Type', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'impact', headerName: 'Impact', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'overlapping_genes', headerName: 'Overlapping Genes', type: "string", flex: 1, headerAlign: 'left' },
  { field: 'cadd_ph', headerName: 'CADD Score', width: 50, type: "number", flex: 1, headerAlign: 'left' },
]

// Default state of each filter.
export const defaultFilters = {
  ref: '',
  alt: '',
  impact: '',
  overlapping_genes: '',
  variant_type: '',
}