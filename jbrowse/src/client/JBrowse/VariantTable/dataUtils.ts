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

export function APIDataToRows(data: any, trackId: string): Row[] {
  return data.map((obj) => ({
    id: obj.genomicPosition,
    chrom: obj.CHROM || "",
    pos: obj.genomicPosition || "",
    start: obj.start || "",
    end: obj.end || "",
    contig: obj.contig || "",
    ref: obj.REF || "",
    alt: obj.ALT || "",
    af: obj.AF || "",
    variant_type: parseAnnField(data.ANN, 1, 'custom') || "",
    impact: obj.impact || "",
    overlapping_genes: parseAnnField(data.ANN, 3, null),
    cadd_ph: obj.CADD_PH || "",
    samples: obj.Samples || "",
    trackId: trackId
  }));
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