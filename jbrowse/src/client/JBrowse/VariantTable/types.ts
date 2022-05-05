// Various interfaces used thoughout VariantTableWidget.
export interface Row {
    chrom: string;
    pos: string;
    ref: string;
    alt: string;
    af: string;
    impact: string;
    variant_type: string;
    overlapping_genes: string;
    cadd_ph: string;
}