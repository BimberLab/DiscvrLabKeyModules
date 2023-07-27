import {
  getGridNumericOperators,
  GridCellParams,
  GridColDef,
  GridComparatorFn,
  GridFilterItem,
  GridFilterOperator
} from '@mui/x-data-grid';
import { arrayMax } from '../utils';
import { parseCellValue } from '../VariantSearch/constants';

// TODO: we need to look into this for all numeric fields. Because some attributes are per-allele,
// The value of these attributes is a CSV string. See comments in dataUtils/rawFeatureToRow()
// const csvNumberFormatFn = (params: GridValueFormatterParams) => {
//   if (!params.value) {
//     return params.value
//   }
//
//   const strVal = String(params.value)
//   const vals = strVal.includes(",") ? strVal.split(',') : [strVal]
//   return vals.map(val => {
//     console.log(val)
//     val ? Number(val).toLocaleString(navigator.language, {maximumFractionDigits: 6}) : val
//   }).join(', ')
// }

const multiValueComparator: GridComparatorFn = (v1, v2) => {
  return arrayMax(parseCellValue(v1)) - arrayMax(parseCellValue(v2))
}

const multiModalOperator = (operator: GridFilterOperator) => {
  const getApplyFilterFn = (
    filterItem: GridFilterItem,
    column: GridColDef,
  ) => {
    const innerFilterFn = operator.getApplyFilterFn(filterItem, column);
    if (!innerFilterFn) {
      return innerFilterFn;
    }

    return (params: GridCellParams) => {
      let cellValue = parseCellValue(params.value)

      switch(filterItem.operator) {
        case "!=":
          return cellValue.map(val => val == Number(filterItem.value)).every((val) => val == false)
        case "=":
          return cellValue.map(val => val == Number(filterItem.value)).includes(true)
        case ">":
          return arrayMax(cellValue) > Number(filterItem.value)
        case "<":
          return arrayMax(cellValue) < Number(filterItem.value)
        case "<=":
          return arrayMax(cellValue) <= Number(filterItem.value)
        case ">=":
          return arrayMax(cellValue) >= Number(filterItem.value)
        case "isEmpty":
          return cellValue.length == 0
        case "isNotEmpty":
          return cellValue.length > 0
        default:
          return true
      }
    }
  }

  return {
    ...operator,
    getApplyFilterFn,
  }
}