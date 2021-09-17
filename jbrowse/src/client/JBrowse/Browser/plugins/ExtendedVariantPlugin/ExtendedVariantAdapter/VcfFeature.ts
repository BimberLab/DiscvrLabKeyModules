import { VcfFeature } from '@jbrowse/plugin-variants'

export default class ExtendedVcfFeature extends VcfFeature {
    constructor(args: { variant: any; parser: any; id: string }) {
        args.variant = ExtendedVcfFeature.extractImpact(args.variant)

        super(args);
    }

    static extractImpact(variant:  {
        REF: string
        POS: number
        ALT: string[]
        CHROM: string
        INFO: any
        ID: string[]
    }) {
        if (!variant.INFO["ANN"]) {
            return;
        }

        let IMPACTs = new Set<String>()
        for (let i = 0; i < variant.INFO["ANN"].length; i++){
            if (/HIGH/g.exec(variant.INFO["ANN"][i])){
                IMPACTs.add("HIGH")
            }
            else if (/MODERATE/g.exec(variant.INFO["ANN"][i])){
                IMPACTs.add("MODERATE")
            }
            else if (/LOW/g.exec(variant.INFO["ANN"][i])){
                IMPACTs.add("LOW")
            }
        }

        variant.INFO["IMPACT"] = null
        if (IMPACTs.has('HIGH')) {
            variant.INFO["IMPACT"] = 'HIGH'
        }
        else if (IMPACTs.has('MODERATE')) {
            variant.INFO["IMPACT"] = 'MODERATE'
        }
        else if (IMPACTs.has('LOW')) {
            variant.INFO["IMPACT"] = 'LOW'
        }

        return(variant)
    }
}