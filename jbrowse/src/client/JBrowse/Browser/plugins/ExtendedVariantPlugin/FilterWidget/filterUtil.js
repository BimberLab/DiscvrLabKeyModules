import {filterMap as fields} from "./filters"

const operators = {
    lt: "<",
    gt: ">",
    eq: "=="
}

export function expandedFilterStringToObj(filter){
// filter: string in the format 'label:expression:selected'
// returns said string as an object
    const splitFilter = filter.split(":")
    return {
        label: splitFilter[0],
        expression: splitFilter[1],
        selected: splitFilter[2]
    }
}

export function expandedFilterListToObj(filters){
// filters: list of strings in the format 'label:expression:selected'
// returns list as objects
    let obj = {}
    for(let i = 0; i < filters.length; i++){
        obj[i] = expandedFilterStringToObj(filters[i])
    }
    return obj
}

export function expandedFilterObjToList(filters){
// filters: obj of expanded filters
// returns as list of expanded filter strings in format 'label:expression:selected'
    let filterList = []
    for (const filter in filters){
        filterList.push(filters[filter].label+":"+filters[filter].expression+":"+filters[filter].selected)
    }
    return filterList

}

export function isFilterStringExpanded(filter){
// filter: string
// returns true if filter fits in expanded filter format
    try {
        let temp = filter.split(":")
        if(temp.length == 3){
            return true
        }
        return false
    } catch {
        return false
    }
}

export function expandFilters(filters) {
// filters: list of strings with properties "label:field:operator:value:selected"
// returns a list of strings "label:expression:selected"
    let filterList = []
    for(const filter of filters){
        try {
            if(isFilterStringExpanded(filter)){
                // if expanded, do not expand
                filterList.push(filter)
                continue
            }
            // TODO type checking, overwriting protection
            const filterProps = filter.split(":") // 0: label  1: field  2: operator  3: value  4: selected
            const label = filterProps[0]
            const fieldLocation = fields[filterProps[1]].location
            const operator = operators[filterProps[2]]
            const value = filterProps[3]
            const selected = filterProps[4]
            const expression = fieldLocation + " " + operator + " " + value
            // should be "AF < 0.2: feature.variant.INFO.AF[0] < 0.2"
            const expandedFilter = label + ":" + expression + ":" + selected
            filterList.push(expandedFilter)
        } catch (e){
            console.error("Error parsing filter - " + e)
        }
    }
    return filterList
}