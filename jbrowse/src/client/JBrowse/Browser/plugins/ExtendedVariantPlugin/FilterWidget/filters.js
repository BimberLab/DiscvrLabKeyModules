export const filterMap = {
    AF: {
    // TODO - add valid operators for each
        title: 'Allele Frequency',
        baseLocation: "AF[0]",
        location: 'feature.variant.INFO.AF[0]'
    },
    AC: {
        title: 'Allele Count',
        baseLocation: 'AC[0]',
        location: 'feature.variant.INFO.AC[0]'
    },
    IMPACT: {
        title: 'Predicted Impact',
        baseLocation: 'IMPACT',
        location: 'feature.variant.INFO.IMPACT'
    }
}