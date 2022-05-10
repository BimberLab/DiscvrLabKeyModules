import type { Row } from './types'
import { passesInfoFilters, passesSampleFilters } from '../utils'
import { deserializeFilters } from '../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil'

// Takes a feature JSON from the API and converts it into a JS object in the schema we want.
export function rawFeatureToRow(rawFeature: any, id: number, trackId: string): Row {
    // TODO: we should pass in the VCF header and do a more complete job of parsing the INFO fields. We should use the datatype (which is defined in the VCF header to more automatically handle type and parsing)
    // See: https://samtools.github.io/hts-specs/VCFv4.2.pdf, and in particular the INFO definitions. Number=A means per-allele, which gives the array
    // ##INFO=<ID=NS,Number=1,Type=Integer,Description="Number of Samples With Data">
    // ##INFO=<ID=DP,Number=1,Type=Integer,Description="Total Depth">
    // ##INFO=<ID=AF,Number=A,Type=Float,Description="Allele Frequency">

  let afString = rawFeature.INFO.AF && rawFeature.INFO.AF.length ? rawFeature.INFO.AF.filter(x => !!x).join(", ") : ""
  let caddPHString = rawFeature.INFO.CADD_PH && rawFeature.INFO.CADD_PH.length ? rawFeature.INFO.CADD_PH.filter(x => !!x).join(", ") : ""
  let altString = rawFeature.ALT && rawFeature.ALT.length ? rawFeature.ALT.filter(x => !!x).join(", ") : ""

  return {
      id: id,
      chrom: (rawFeature.CHROM ?? "-1").toString(),
      pos: (rawFeature.POS ?? "-1").toString(),
      ref: (rawFeature.REF ?? "").toString(),
      alt: altString,
      af: afString,
      impact: (rawFeature.INFO.IMPACT ?? "").toString(),
      overlapping_genes: parseAnnField(rawFeature.INFO.ANN, 3, null),
      variant_type: parseAnnField(rawFeature.INFO.ANN, 1, 'custom'),
      cadd_ph: caddPHString,
      start: rawFeature.start,
      end: rawFeature.end,
      trackId: trackId
  } as Row
}

// Takes a list of ANN annotations and retrieves all unique genes from it.
function parseAnnField(anns, fieldIdx, ignoredTerms) {
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
    if(passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
      ret.push(feature)
    }
  })

  return ret
}