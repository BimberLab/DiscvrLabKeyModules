import type { Row } from './types'
import { passesInfoFilters, passesSampleFilters } from '../utils'
import { deserializeFilters } from '../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil'

// Logic behind how to filter each field.
export function filterFeature(feature, filters) {
    return (
        (filters.ref ? feature.ref.includes(filters.ref) : true) &&
        (filters.alt ? feature.alt.includes(filters.alt) : true) &&
        (filters.impact ? feature.impact.includes(filters.impact) : true) &&
        (filters.variant_type ? feature.variant_type.includes(filters.variant_type) : true) &&
        (filters.overlapping_genes ? feature.overlapping_genes.includes(filters.overlapping_genes) : true)
    )
}

// Utility function to apply getComparator, which contains our sorting logic, to each column.
export function sortFeatures(features, sortColumns) {
  if (sortColumns.length === 0) return features;

  return [...features].sort((a, b) => {
    for (const sort of sortColumns) {
      const comparator = getComparator(sort.columnKey);
      const compResult = comparator(a, b);
      if (compResult !== 0) {
        return sort.direction === 'ASC' ? compResult : -compResult;
      }
    }
    return 0;
  });
}

// Contains logic for how to compare values within a column to themselves.
type Comparator = (a: Row, b: Row) => number;
function getComparator(sortColumn: string): Comparator {
  switch (sortColumn) {
    case 'ref':
    case 'alt':
    case 'impact':
    case 'variant_type':
    case 'overlapping_genes':
      return (a, b) => {
        return a[sortColumn].localeCompare(b[sortColumn]);
      };
    case 'cadd_ph':
    case 'chrom':
    case 'pos':
    case 'af':
      return (a, b) => {
        return Number(a[sortColumn]) - Number(b[sortColumn]);
      };
    default:
      throw new Error(`unsupported sortColumn: "${sortColumn}"`);
  }
}


// Takes a feature JSON from the API and converts it into a JS object in the schema we want.
export function rawFeatureToRow(rawFeature: any, id: number): Row {
  let afString = ""
  if(rawFeature.data.INFO.AF && rawFeature.data.INFO.AF.length > 1) {
    afString = rawFeature.data.INFO.AF.join(", ")
    afString = afString.substring(0, afString.length - 2)
  } else if (rawFeature.data.INFO.AF && rawFeature.data.INFO.AF.length === 1) {
    afString = rawFeature.data.INFO.AF[0]
  }

  let caddPHString = ""
  if(rawFeature.data.INFO.CADD_PH && rawFeature.data.INFO.CADD_PH > 1) {
    caddPHString = rawFeature.data.INFO.CADD_PH.join(", ")
    caddPHString = caddPHString.substring(0, caddPHString.length - 2)
  } else if (rawFeature.data.INFO.CADD_PH && rawFeature.data.INFO.CADD_PH.length === 1) {
    caddPHString = rawFeature.data.INFO.CADD_PH[0]
  }

  let row = {
      id: id,
      chrom: (rawFeature.data.CHROM ?? "-1").toString(),
      pos: (rawFeature.data.POS ?? "-1").toString(),
      ref: (rawFeature.data.REF ?? "").toString(),
      alt: (rawFeature.data.ALT[0] ?? "").toString(),
      af: afString,
      impact: (rawFeature.data.INFO.IMPACT ?? "").toString(),
      overlapping_genes: generateGeneList(rawFeature.data.INFO.ANN, 3, null),
      variant_type: generateGeneList(rawFeature.data.INFO.ANN, 1, 'custom'),
      cadd_ph: caddPHString
  } as Row

  return row
}


// Takes a list of ANNS and retrieves all unique genes from it.
function generateGeneList(anns, fieldIdx, ignoredTerms) {
  let geneSet = new Set()
  for(let ann of anns) {
    let geneName = ann.split("|")[fieldIdx]
    if (ignoredTerms && ignoredTerms.includes(geneName)) {
      continue
    }

    if (geneName) {
      geneSet.add(geneName)
    }
  }

  return Array.from(geneSet).join(", ")
}

// Filters features according to the data from the relevant widgets
export function filterFeatures(features, activeSamples, filters) {
  let ret = []

  let processedActiveSamples = activeSamples === "" ? [] : activeSamples.split(",")
  let processedFilters = deserializeFilters(JSON.parse(filters))

  features.forEach((feature) => {
    if(passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
      ret.push(feature)
    }
  })


  return ret
}