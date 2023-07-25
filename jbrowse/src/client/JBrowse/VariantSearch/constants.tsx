import {
  GridCellParams,
  GridComparatorFn,
  GridFilterItem,
  GridFilterOperator,
  GridStateColDef
} from '@mui/x-data-grid';
import { arrayMax } from '../utils';

export const parseCellValue = (cellValue) => String(cellValue ?? "").split(",").map(str => {
  return Number(str);
})

export const multiValueComparator: GridComparatorFn = (v1, v2) => {
  return arrayMax(parseCellValue(v1)) - arrayMax(parseCellValue(v2))
}

export const multiModalOperator = (operator: GridFilterOperator) => {
  const getApplyFilterFn = (
    filterItem: GridFilterItem,
    column: GridStateColDef,
  ) => {
    const innerFilterFn = operator.getApplyFilterFn(filterItem, column);
    if (!innerFilterFn) {
      return innerFilterFn;
    }

    return (params: GridCellParams) => {
      let cellValue = parseCellValue(params.value)

      switch(filterItem.operatorValue) {
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