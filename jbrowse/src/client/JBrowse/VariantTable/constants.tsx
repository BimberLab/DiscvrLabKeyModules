import { GridColumns, GridComparatorFn, getGridNumericColumnOperators, GridFilterOperator, GridFilterItem, GridStateColDef, GridCellParams } from '@mui/x-data-grid';
import { arrayMax } from '../utils'

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
const parseCellValue = (cellValue) => (cellValue.valueOf() as string).split(",").map(str => {
  return Number(str);
})

const multiValueComparator: GridComparatorFn = (v1, v2) => {
  return arrayMax(parseCellValue(v1)) - arrayMax(parseCellValue(v2))
}

const multiModalOperator = (operator: GridFilterOperator) => {
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
      console.log(filterItem.operatorValue)

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

// Columns to be shown, minus the ID column.
export const columns: GridColumns = [
  { field: 'chrom', headerName: 'Chromosome', width: 150, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'pos', headerName: 'Position', width: 150, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'ref', headerName: 'Reference', width: 150, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'alt', headerName: 'Alternative Allele', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { 
    field: 'af', 
    headerName: 'Allele Frequency',
    width: 50,
    type: "number",
    flex: 1,
    headerAlign: 'left',
    sortComparator: multiValueComparator,
    filterOperators: getGridNumericColumnOperators().map(op => multiModalOperator(op))
  },
  { field: 'variant_type', headerName: 'Type', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'impact', headerName: 'Impact', width: 50, type: "string", flex: 1, headerAlign: 'left' },
  { field: 'overlapping_genes', headerName: 'Overlapping Genes', type: "string", flex: 1, headerAlign: 'left' },
  { field: 'cadd_ph', headerName: 'CADD Score', width: 50, type: "number", flex: 1, headerAlign: 'left' },
  { field: 'track_id', headerName: 'Track ID', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true, filterable: false},
  { field: 'start', headerName: 'Start Location', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true, filterable: false },
  { field: 'end', headerName: 'End Location', width: 50, type: "string", flex: 1, headerAlign: 'left', hide: true, filterable: false }
]