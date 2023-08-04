import { VcfFeature } from '@jbrowse/plugin-variants'
import VcfParser from '@gmod/vcf'

export default class ExtendedVcfFeature extends VcfFeature {
    private readonly vcfParser: VcfParser

    constructor(args: { variant: any; parser: VcfParser; id: string }) {
        args.variant = ExtendedVcfFeature.extractImpact(args.variant)
        //args.variant = ExtendedVcfFeature.calculateVariableSamples(args.variant)

        super(args)

        this.vcfParser = args.parser
    }

    public getInfoFieldMeta(propKey: string): VcfParser {
        const map = this.vcfParser.getMetadata("INFO")

        return map ? map[propKey] :  null
    }

    static extractImpact(variant:  {
        REF: string
        POS: number
        ALT: string[]
        CHROM: string
        INFO: any
        ID: string[]
    }) {
        // Only append if not present:
        if (variant.INFO["IMPACT"]) {
            return(variant);
        }

        if (!variant.INFO["ANN"]) {
            return(variant);
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