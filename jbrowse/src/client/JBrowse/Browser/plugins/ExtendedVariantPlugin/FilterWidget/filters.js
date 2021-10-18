
// TODO - map operators to data type?
export const filterMap = {
    AF: {
        title: 'Allele Frequency',
        operators: ["<", ">", "=="],
        dataType: "number",
        maxValue: 100, // TODO - need values
        minValue: 0,
        location: 'feature.variant.INFO.AF[0]'
    },
    AC: {
        title: 'Allele Count',
        operators: ["<", ">", "=="],
        dataType: "number",
        maxValue: 100, // TODO - need values
        minValue: 0,
        location: 'feature.variant.INFO.AC[0]'
    },
    IMPACT: {
        title: 'Predicted Impact',
        operators: ["=="],
        dataType: "string",
        options: ["HIGH", "MODERATE", "LOW"],
        location: 'feature.variant.INFO.IMPACT'
    }
}