// Various interfaces used thoughout VariantTableWidget.
export interface Filter extends Omit<Row, 'id'> {
    chrom: string;
    pos: string;
    ref: string;
    alt: string;
    af: string;
    impact: string;
    overlapping_genes: string;
    cadd_ph: string;
  }
   
export interface Row {
    id: number;
    chrom: string;
    pos: string;
    ref: string;
    alt: string;
    af: string;
    impact: string;
    overlapping_genes: string;
    cadd_ph: string;
}