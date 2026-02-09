import { VcfFeature } from '@jbrowse/plugin-variants'
import VcfParser, { Variant } from '@gmod/vcf';

export default class ExtendedVcfFeature extends VcfFeature {
    constructor(args: { variant: Variant; parser: VcfParser; id: string }) {
        args.variant = ExtendedVcfFeature.extractImpact(args.variant)

        super(args)
    }

    toJSON() {
        const ret: any = super.toJSON()

        // Preserve VCF INFO header metadata through JSON/snapshot flows used by
        // JBrowse widgets. parser is not guaranteed to survive serialization.
        ret.vcfMetadataInfo = (this as any).parser?.metadata?.INFO || {}

        return ret
    }

    static extractImpact(variant: Variant) {
        // Only append if not present:
        if (variant.INFO["IMPACT"]) {
            return(variant);
        }

        const ann = variant.INFO["ANN"] as string[] | undefined
        if (!ann) {
            return(variant);
        }

        let IMPACTs = new Set<String>()
        for (let i = 0; i < ann.length; i++){
            if (/HIGH/g.exec(ann[i])){
                IMPACTs.add("HIGH")
            }
            else if (/MODERATE/g.exec(ann[i])){
                IMPACTs.add("MODERATE")
            }
            else if (/LOW/g.exec(ann[i])){
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