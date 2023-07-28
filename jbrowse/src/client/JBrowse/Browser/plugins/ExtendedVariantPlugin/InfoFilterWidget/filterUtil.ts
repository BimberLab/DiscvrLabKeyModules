import { filterMap } from './filters';

export const operators = {
    lt: "<",
    gt: ">",
    eq: "=="
}

export function isSerializedFilterStringValid(filter) {
    if (!filter) {
        console.error('Invalid filter string: ' + filter)
        return false;
    }

    const filterProps = filter.split(":")
    if (filterProps.length !== 3){
        console.error("Invalid filter, does not have a length of 3: " + filter)
        return false;
    }

    const fieldName = filterProps[0]
    if (!(fieldName in filterMap)){
        console.error("Invalid filter, field not found: " + filter)
        return false
    }

    const op = filterProps[1]
    if (filterMap[fieldName].operators.indexOf(op) === -1){
        console.error("Invalid operator for filter: " + filter)
        return false
    }

    const value = filterProps[2]
    let dataType = filterMap[fieldName].dataType
    if (dataType === "number" && isNaN(value)) {
        console.error("Invalid filter, expected a number: " + filter)
        return false
    }
    else if (dataType === "string"){
        if (filterMap[fieldName].options.indexOf(value) === -1) {
            console.error("Invalid value for filter: " + filter)
            return false
        }
    }

    return true;
}

export function deserializeFilters(filters) {
    let filterList = []
    if (!filters){
        return filterList
    }

    if (!Array.isArray(filters)) {
        filters = [filters]
    }

    for (const filter of filters){
        if (!isSerializedFilterStringValid(filter)) {
            continue
        }

        const filterProps = filter.split(":")
        let fieldObj = {
            field: filterProps[0],
            operator: filterProps[1],
            value: filterProps[2],
            jexlExpression: null
        }

        const fieldDef = filterMap[fieldObj.field]
        const quoteChar = fieldDef.dataType === 'string' ? "'" : ""
        fieldObj.jexlExpression = fieldDef.location + operators[fieldObj.operator] + quoteChar + fieldObj.value + quoteChar

        filterList.push(fieldObj)
    }

    return filterList
}