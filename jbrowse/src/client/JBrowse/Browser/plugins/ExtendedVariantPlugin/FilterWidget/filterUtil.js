import {filterMap as fields} from "./filters"

export const operators = {
    lt: "<",
    gt: ">",
    eq: "=="
}
export function unexpandedFilterStringToObj(filter){
// filter: string in the format 'field:operator:value'
// returns said string as an object
   const splitFilter = filter.split(":")
   return {
      field: splitFilter[0],
      operator: splitFilter[1],
      value: splitFilter[2]
   }
}

export function unexpandedFilterObjToString(filter){
    const filterString = filter.field + ":" + filter.operator + ":" + filter.value
    return filterString
}

export function expandedFilterStringToObj(filter){
// filter: string in the format 'field:expression'
// returns said string as an object
    const splitFilter = filter.split(":")
    return {
        field: splitFilter[0],
        expression: splitFilter[1]
    }
}

export function expandedFilterListToObj(filters){
// filters: list of strings in the format 'field:expression'
// returns list as objects
    let obj = {}
    for(let i = 0; i < filters.length; i++){
        obj[i] = expandedFilterStringToObj(filters[i])
    }
    return obj
}

export function expandedFilterObjToList(filters){
// filters: obj of expanded filters
// returns as list of expanded filter strings in format 'label:expression'
    let filterList = []
    for (const filter in filters){
        filterList.push(filters[filter].label+":"+filters[filter].expression)
    }
    return filterList

}

export function isFilterStringExpanded(filter){
// filter: string
// returns true if filter fits in expanded filter format
    try {
        let temp = filter.split(":")
        if(temp.length == 2){
            return true
        }
        return false
    } catch {
        return false
    }
}

export function expandFilters(filters) {
<<<<<<< HEAD
// filters: list of strings with properties "label:field:operator:value:selected"
// returns a list of strings "label:expression:selected"
// 'expanded' indicates that the filter's field, operator and values have been combined into their full functioning jexl expression.
// unexpanded ex: "AF < 0.1:AF:lt:0.1:false"
// expanded ex:   "AF < 0.1:variant.INFO.AF[0]<0.1:false"
=======
// filters: list of strings with properties "field:operator:value"
// returns a list of strings "field:expression:"
// 'expanded' indicates that the filter's field, operator and values have been combined into their full functioning expression.
// unexpanded ex: "AF:lt:0.1"
// expanded ex:   "AF:feature.variant.INFO.AF[0] < 0.1"

>>>>>>> 21.7_fb_filter-ui
    let filterList = []
    if(!filters){
        return filterList
    }
    for(const filter of filters){
        try {
            if(isFilterStringExpanded(filter)){
                // if expanded, do not expand
                filterList.push(filter)
                continue
            }
            const filterProps = filter.split(":") // 0: label  1: field  2: operator  3: value  4: selected
            const field = filterProps[0]
            const rawOperator = filterProps[1]
            const value = filterProps[2]
             if(!(field && rawOperator && value)){
                // if any prop is null, do not make filter
                continue
             }
             const fieldLocation = fields[field].baseLocation;
             const operator = operators[rawOperator];
             const jexlExpression = "variant.INFO." + fieldLocation + operator + value
             const expandedFilter = field + ":" + jexlExpression
            filterList.push(expandedFilter)
        } catch (e){
            console.error("Error parsing filter - " + e)
        }
    }
    return filterList
}
