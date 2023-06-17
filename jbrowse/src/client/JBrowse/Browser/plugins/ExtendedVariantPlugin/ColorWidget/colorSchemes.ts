export const colorSchemes = {
    IMPACT: {
        title: 'Predicted Impact',
        dataType: "option",
        options: {
            HIGH: "red",
            MODERATE: "goldenrod",
            LOW: "#049931",
        },
        jexlComponent: "get(feature,'INFO').IMPACT",
    },
    AF: {
        title: "Allele Frequency",
        dataType: "number",
        options: {
            minVal: "#0c28f9", // for numbers, this must be a hex code rather than a color string (i.e. "red")
            maxVal: "#f90c00"
        },
        minVal: 0,
        maxVal: 1,
        gradientSteps: 10,
        displaySigFigs: 3,
        jexlComponent: "arrayMax(get(feature,'INFO').AF)",
    },
}