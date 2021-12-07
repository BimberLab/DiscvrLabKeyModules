
export const filterMap = {
    AF: {
        title: 'Allele Frequency',
        baseLocation: "AF[0]",
        operators: ["lt", "gt", "eq"],
        dataType: "number",
        maxValue: 1,
        minValue: 0,
        location: 'variant.INFO.AF[0]'
    },
    AC: {
        title: 'Allele Count',
        baseLocation: 'AC[0]',
        operators: ["lt", "gt", "eq"],
        dataType: "number",
        maxValue: null,
        minValue: 0,
        location: 'variant.INFO.AC[0]'
    },
    IMPACT: {
        title: 'Predicted Impact',
        baseLocation: 'IMPACT',
        operators: ["eq"],
        dataType: "string",
        options: ["HIGH", "MODERATE", "LOW"], // right now if dataType is string, the object must have an options category
        location: 'variant.INFO.IMPACT'
    }
}