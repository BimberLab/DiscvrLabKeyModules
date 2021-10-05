export const filterMap = {
    AF: {
    // TODO - add valid operators for each
        title: 'Allele Frequency',
        location: 'feature.variant.INFO.AF[0]'
    },
    AC: {
        title: 'Allele Count',
        location: 'feature.variant.INFO.AC[0]'
    },
    IMPACT: {
        title: 'Predicted Impact',
        location: 'feature.variant.INFO.IMPACT'
    }
}