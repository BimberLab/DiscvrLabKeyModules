import type { Row } from './types'

// Logic behind how to filter each field.
export function filterFeature(feature, filters) {
    return (
        (filters.chrom ? feature.chrom.includes(filters.chrom) : true) &&
        (filters.pos ? feature.pos.includes(filters.pos) : true) &&
        (filters.ref ? feature.ref.includes(filters.ref) : true) &&
        (filters.alt ? feature.alt.includes(filters.alt) : true) &&
        (filters.af ? feature.af.includes(filters.af) : true) &&
        (filters.impact ? feature.impact.includes(filters.impact) : true) &&
        (filters.overlapping_genes ? feature.overlapping_genes.includes(filters.overlapping_genes) : true) &&
        (filters.cadd_ph ? feature.cadd_ph.includes(filters.cadd_ph) : true)
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
    case 'chrom':
    case 'pos':
    case 'ref':
    case 'alt':
    case 'impact':
    case 'overlapping_genes':
      return (a, b) => {
        return a[sortColumn].localeCompare(b[sortColumn]);
      };
    case 'cadd_ph':
    case 'id':
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
  if(rawFeature.variant.INFO.AF && rawFeature.variant.INFO.AF.length > 1) {
    afString = rawFeature.variant.INFO.AF.join(", ")
    afString = afString.substring(0, afString.length - 2)
  } else if (rawFeature.variant.INFO.AF && rawFeature.variant.INFO.AF.length === 1) {
    afString = rawFeature.variant.INFO.AF[0]
  }

  let caddPHString = ""
  if(rawFeature.variant.INFO.CADD_PH && rawFeature.variant.INFO.CADD_PH > 1) {
    caddPHString = rawFeature.variant.INFO.CADD_PH.join(", ")
    caddPHString = caddPHString.substring(0, caddPHString.length - 2)
  } else if (rawFeature.variant.INFO.CADD_PH && rawFeature.variant.INFO.CADD_PH.length === 1) {
    caddPHString = rawFeature.variant.INFO.CADD_PH[0]
  }

  let row = {
      id: id,
      chrom: (rawFeature.variant.CHROM ?? "-1").toString(),
      pos: (rawFeature.variant.POS ?? "-1").toString(),
      ref: (rawFeature.variant.REF ?? "").toString(),
      alt: (rawFeature.variant.ALT[0] ?? "").toString(),
      af: afString,
      impact: (rawFeature.variant.INFO.IMPACT ?? "").toString(),
      overlapping_genes: generateGeneList(rawFeature.variant.INFO.ANN),
      cadd_ph: caddPHString
  } as Row

  return row
}


// Takes a list of ANNS and retrieves all unique genes from it.
function generateGeneList(anns) {
  let geneSet = new Set()
  for(let ann of anns) {
    let geneName = ann.split("|")[3]
    geneSet.add(geneName)
  }
  
  let ret = Array.from(geneSet).join(", ")
  return ret.substring(0, ret.length - 2)
}