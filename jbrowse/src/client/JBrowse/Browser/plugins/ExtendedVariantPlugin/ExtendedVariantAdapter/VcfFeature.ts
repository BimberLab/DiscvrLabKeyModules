import {default as VCFFeature} from '@jbrowse/plugin-variants/src/VcfTabixAdapter/VcfFeature'
import { Breakend } from '@jbrowse/plugin-variants/src/VcfTabixAdapter/VcfFeature'

export default class ExtendedVcfFeature extends VCFFeature {

  dataFromVariant(variant: any) {
    const { REF, ALT, POS, CHROM, INFO, ID } = variant
    let IMPACT = null
    let regex = /HIGH/
    if(INFO["ANN"]){
        for(let i = 0; i < INFO["ANN"].length; i++){
            if(/HIGH/g.exec(INFO["ANN"][i])){
                IMPACT = "HIGH"
            }
            else if(IMPACT != "HIGH" && /MODERATE/g.exec(INFO["ANN"][i])){
                IMPACT = "MODERATE"
            }
            else if(IMPACT != "HIGH" && IMPACT != "MODERATE" && /LOW/g.exec(INFO["ANN"][i])){
                IMPACT = "LOW"
            }
        }
    }
    INFO["IMPACT"] = IMPACT
    const start = POS - 1
    const [SO_term, description] = this._getSOTermAndDescription(REF, ALT)
    const isTRA = ALT && ALT.some((f: string | Breakend) => f === '<TRA>')
    const isSymbolic =
      ALT &&
      ALT.some(
        (f: string | Breakend) =>
          typeof f === 'string' && f.indexOf('<') !== -1,
      )
    const featureData = {
      refName: CHROM,
      start,
      end: isSymbolic && INFO.END && !isTRA ? +INFO.END[0] : start + REF.length,
      description,
      type: SO_term,
      name: ID ? ID[0] : undefined,
      aliases: ID && ID.length > 1 ? variant.ID.slice(1) : undefined
    }

    return featureData
  }
}