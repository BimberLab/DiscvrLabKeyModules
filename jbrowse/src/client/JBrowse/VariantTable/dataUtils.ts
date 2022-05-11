import type { Row } from './types';
import { passesInfoFilters, passesSampleFilters } from '../utils';
import { deserializeFilters } from '../Browser/plugins/ExtendedVariantPlugin/InfoFilterWidget/filterUtil';
import ExtendedVcfFeature from '../Browser/plugins/ExtendedVariantPlugin/ExtendedVariantAdapter/ExtendedVcfFeature';

// Takes a feature JSON from the API and converts it into a JS object in the schema we want.
export function rawFeatureToRow(rawFeature: ExtendedVcfFeature, id: number, trackId: string): Row {
    // TODO: we should pass in the VCF header and do a more complete job of parsing the INFO fields. We should use the datatype (which is defined in the VCF header to more automatically handle type and parsing)
    // See: https://samtools.github.io/hts-specs/VCFv4.2.pdf, and in particular the INFO definitions. Number=A means per-allele, which gives the array
    // ##INFO=<ID=NS,Number=1,Type=Integer,Description="Number of Samples With Data">
    // ##INFO=<ID=DP,Number=1,Type=Integer,Description="Total Depth">
    // ##INFO=<ID=AF,Number=A,Type=Float,Description="Allele Frequency">
  let afString = rawFeature.get("INFO").AF && rawFeature.get("INFO").AF.length ? rawFeature.get("INFO").AF.filter(x => !!x).join(", ") : ""
  let caddPHString = rawFeature.get("INFO").CADD_PH && rawFeature.get("INFO").CADD_PH.length ? rawFeature.get("INFO").CADD_PH.filter(x => !!x).join(", ") : ""
  let altString = rawFeature.get("ALT") && rawFeature.get("ALT").length ? rawFeature.get("ALT").filter(x => !!x).join(", ") : ""

  return {
      id: id,
      chrom: (rawFeature.get("CHROM") ?? "-1").toString(),
      pos: (rawFeature.get("POS") ?? "-1").toString(),
      ref: (rawFeature.get("REF") ?? "").toString(),
      alt: altString,
      af: afString,
      impact: (rawFeature.get("INFO").IMPACT ?? "").toString(),
      overlapping_genes: parseAnnField(rawFeature.get("INFO").ANN, 3, null),
      variant_type: parseAnnField(rawFeature.get("INFO").ANN, 1, 'custom'),
      cadd_ph: caddPHString,
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
    if(passesSampleFilters(feature, processedActiveSamples) && passesInfoFilters(feature, processedFilters)) {
      ret.push(feature)
    }
  })

  return ret
}