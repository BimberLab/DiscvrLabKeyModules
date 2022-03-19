// Columns to be shown, minus the ID column.
export const columnsObjRaw = [
  { key: 'chrom', name: 'Chromosome' },
  { key: 'pos', name: 'Position' },
  { key: 'ref', name: 'Reference' },
  { key: 'alt', name: 'ALT' },
  { key: 'af', name: 'AF' },
  { key: 'impact', name: 'IMPACT' },
  { key: 'overlapping_genes', name: 'Overlapping Genes' },
  { key: 'cadd_ph', name: 'CADD_PH' },
]

// Default state of each filter.
export const defaultFilters = {
  chrom: '',
  pos: '',
  ref: '',
  alt: '',
  af: '',
  impact: '',
  overlapping_genes: '',
  cadd_ph: ''
}