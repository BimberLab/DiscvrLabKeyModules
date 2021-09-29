export const filterMap = {
    af02: {
        selected: false,
        title: 'AF < 0.2',
        expression: 'feature.variant.INFO.AF[0] < 0.2'
    },
    ac80: {
        selected: false,
        title: 'AC < 80',
        expression: 'feature.variant.INFO.AC[0] < 80'
    },
    impactHigh: {
        selected: false,
        title: 'Impact = HIGH',
        expression: 'feature.variant.INFO.IMPACT === "HIGH"'
    }
}