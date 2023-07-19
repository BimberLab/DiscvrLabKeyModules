import type { Row } from './types';
import { passesInfoFilters, passesSampleFilters } from '../utils';
import { deserializeFilters } from '../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil';
import ExtendedVcfFeature from '../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter/ExtendedVcfFeature';

const prepareInfoField = (rawFeature: ExtendedVcfFeature, propKey: string) => {
    //const info = rawFeature.getInfoFieldMeta(propKey)
    const rawVal = rawFeature.get("INFO")[propKey]
    if (Array.isArray(rawVal)) {
        return(rawVal.filter(x => x !== null && x !== '').join(", ") || "")
    }
    else {
        return(rawVal)
    }
}

// Takes a feature JSON from the API and converts it into a JS object in the schema we want.
export function rawFeatureToRow(rawFeature: ExtendedVcfFeature, id: number, trackId: string): Row {
  return {
      id: id,
      chrom: (rawFeature.get("CHROM") ?? "-1").toString(),
      pos: (rawFeature.get("POS") ?? "-1").toString(),
      ref: (rawFeature.get("REF") ?? "").toString(),
      alt: rawFeature.get("ALT") && rawFeature.get("ALT").length ? rawFeature.get("ALT").filter(x => !!x).join(", ") : "",
      af: prepareInfoField(rawFeature, "AF"),
      impact: (rawFeature.get("INFO").IMPACT ?? "").toString(),
      overlapping_genes: parseAnnField(rawFeature.get("INFO").ANN, 3, null),
      variant_type: parseAnnField(rawFeature.get("INFO").ANN, 1, 'custom'),
      cadd_ph: prepareInfoField(rawFeature, "CADD_PH"),
      start: rawFeature.get("POS"),
      end: rawFeature.get("end"),
      trackId: trackId
  } as Row
}

// Takes a list of ANN annotations and retrieves all unique genes from it.
function parseAnnField(anns, fieldIdx, ignoredTerms) {
  if (!anns) {
    return ''
  }

  let geneSet = new Set()
  for (let ann of anns) {
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
    if (passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
      ret.push(feature)
    }
  })

  return ret
}