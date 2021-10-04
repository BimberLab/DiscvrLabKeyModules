// NOTE: this should centralize all the logic we need around rendering the filter UI and
// building the filter functions. This currently hard-coded the filter value and operator, but
// eventually this should be extended.
export const filterMap = {
    af: {
        selected: false,
        title: 'Allele Frequency',
        datatype: 'float',
        minValue: 0.0,
        maxValue: 1.0,
        expression: 'feature.variant.INFO.AF[0]'
    },
    ac: {
        selected: false,
        title: 'Allele Count',
        datatype: 'integer',
        minValue: 0,
        maxValue: null,
        expression: 'feature.variant.INFO.AC[0]'
    },
    impact: {
        selected: false,
        title: 'Predicted Impact',
        datatype: 'string',
        allowableValues: ['HIGH', 'LOW', 'MODERATE'],
        expression: 'feature.variant.INFO.IMPACT'
    }
}

function createFilterJexl(filterStr) {
    if (!filterStr) {
        return null;
    }

    const parts = filterStr.split(';')
    const filter = filterMap[parts[0]]
    if (!filter) {
        console.error('Unknown filter type: ' + parts[0])
        return null;
    }

    const doQuote = filter.datatype === 'string'
    return filter.expression + parts[1] + (doQuote ? '"' : '') + parts[2] + (doQuote ? '"' : '')
}