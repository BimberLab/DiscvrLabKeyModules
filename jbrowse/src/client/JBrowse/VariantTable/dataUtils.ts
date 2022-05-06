import type { Row } from './types'
import { passesInfoFilters, passesSampleFilters } from '../utils'
import { deserializeFilters } from '../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil'

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