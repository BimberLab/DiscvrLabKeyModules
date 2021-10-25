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
        if(temp.length == 2 || temp[1].includes('variant.INFO.')){
            return true
        }
        return false
    } catch {
        return false
    }
}

export function isFilterFieldValid(filterField){
   try{
      if(fields[filterField]){
         return true
      }
   } catch (e){
      return false
   }
   return false
}

export function isUnexpandedFilterValid(filter){
   try{
      const filterProps = filter.split(":")
      if (filterProps.length != 3){
         console.error("Invalid filter - " + filter + " does not have a length of 3")
         return false
      }
      let key = filterProps[0] in fields
      if (!key){
         console.error("Invalid filter - " + filterProps[0] + " in " + filter + " is not a valid field.")
         return false
      }
      if(filterProps[1] != ""){
         if (fields[filterProps[0]].operators.indexOf(operators[filterProps[1]]) < 0){
            console.error("Invalid filter - " + filterProps[1] + " in " + filter + " is not a valid operator.")
            return false
         }
      }
      if(filterProps[2] != ""){
         let dataType = fields[filterProps[0]].dataType
         if(dataType == "number" && isNaN(filterProps[2])){
            console.error("Invalid filter - " + filterProps[2] + " in " + filter + " is not valid. Expected a number.")
            return false
         }
         if(dataType == "string"){
            if (fields[filterProps[0]].options.indexOf(filterProps[2].replaceAll("'", "")) < 0){
               console.error("Invalid filter - " + filterProps[2] + " in " + filter + " is not a valid option for " + filterProps[0] + ". Enter one of the following - " + fields[filterProps[0]].options)
               return false
            }
            if (filterProps[2].replaceAll("'", "") == filterProps[2]){
               console.error("Invalid filter - " + filterProps[2] + " in " + filter + " is not wrapped in \" ' \".")
               return false
            }
         }
      }
      return true

   }catch (e){
      console.error("Error validating filter " + filter + " - " + e)
      return false
   }
}

export function removeInvalidUnexpandedFilters(filters){
   let filterList = []
   if(!filters){
      return filterList
   }
   for(const filter of filters){
      if(isFilterStringExpanded(filter)){
         continue
      }
      if(isUnexpandedFilterValid(filter)){
         filterList.push(filter)
      }
   }
   return filterList
}

export function expandFilters(filters) {
// filters: list of strings with properties "field:operator:value"
// returns a list of strings "field:expression:"
// 'expanded' indicates that the filter's field, operator and values have been combined into their full functioning expression.
// unexpanded ex: "AF:lt:0.1"
// expanded ex:   "AF:variant.INFO.AF[0] < 0.1"

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
            if(!isUnexpandedFilterValid(filter)){
               // if invalid filter, skip it
               continue
            }
            const filterProps = filter.split(":")
            const field = filterProps[0]
            const rawOperator = filterProps[1]
            const value = filterProps[2]
             if(!(field && rawOperator && value && isFilterFieldValid(field))){
                // if any prop is null or field is not valid, do not make filter
                continue
             }
             const fieldLocation = fields[field].baseLocation;
             const operator = operators[rawOperator];
             const jexlExpression = "variant.INFO." + fieldLocation + operator + value
             const expandedFilter = field + ":" + jexlExpression
            filterList.push(expandedFilter)
        } catch (e){
            console.error("Error parsing filter, skipping. " + e)
            continue
        }
    }
    return filterList
}
