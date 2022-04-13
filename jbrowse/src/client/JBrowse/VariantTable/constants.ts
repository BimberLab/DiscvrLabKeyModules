// Columns to be shown, minus the ID column.
export const columnsObjRaw = [
  { key: 'chrom', name: 'Chromosome', type: "number" },
  { key: 'pos', name: 'Position', type: "number" },
  { key: 'ref', name: 'Reference', type: "string" },
  { key: 'alt', name: 'Alternative Allele', type: "string" },
  { key: 'af', name: 'Allele Frequency', type: "number" },
  { key: 'variant_type', name: 'Type', type: "string" },
  { key: 'impact', name: 'Impact', type: "string" },
  { key: 'overlapping_genes', name: 'Overlapping Genes', type: "string" },
  { key: 'cadd_ph', name: 'CADD Score', type: "number" },
]

// Default state of each filter.
export const defaultFilters = {
  ref: '',
  alt: '',
  impact: '',
  overlapping_genes: '',
  variant_type: '',
}