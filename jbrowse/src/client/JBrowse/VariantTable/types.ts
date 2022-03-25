// Various interfaces used thoughout VariantTableWidget.
export interface Filter extends Omit<Row, 'chrom'|'pos'|'af'|'cadd_ph'> {
    ref: string;
    alt: string;
    impact: string;
    overlapping_genes: string;
  }
   
export interface Row {
    chrom: string;
    pos: string;
    ref: string;
    alt: string;
    af: string;
    impact: string;
    overlapping_genes: string;
    cadd_ph: string;
}