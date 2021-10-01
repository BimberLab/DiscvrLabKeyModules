// NOTE: this should centralize all the logic we need around rendering the filter UI and
// building the filter functions. This currently hard-coded the filter value and operator, but
// eventually this should be extended.
export const filterMap = {
    af: {
        selected: false,
        title: 'Allele Frequency',
        expression: 'feature.variant.INFO.AF[0] < 0.2'
    },
    ac: {
        selected: false,
        title: 'Allele Count',
        //TODO: ultimately configure these strings, maybe like:
        //expression: 'feature.variant.INFO.AC[0] {OP} {VAL}'
        expression: 'feature.variant.INFO.AC[0] > 80'
    },
    impact: {
        selected: false,
        title: 'Predicted Impact',
        expression: 'feature.variant.INFO.IMPACT === "HIGH"'
    }
}