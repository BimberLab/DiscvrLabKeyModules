import QuickLRU from '@jbrowse/core/util/QuickLRU';
import { BaseOptions } from '@jbrowse/core/data_adapters/BaseAdapter';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import { ObservableCreate } from '@jbrowse/core/util/rxjs';
import { Feature } from '@jbrowse/core/util/simpleFeature';
import ExtendedVcfFeature from './ExtendedVcfFeature';
import { VcfFeature } from '@jbrowse/plugin-variants';
import { default as VcfTabixAdapter } from './VcfTabixAdapter';

export default class extends VcfTabixAdapter {
    protected featureCache = new QuickLRU({ maxSize: 20 })

    public getFeatures(query: NoAssemblyRegion, opts: BaseOptions = {}) {
        return ObservableCreate<Feature>(async observer => {
            const { refName, start, end } = query
            // NOTE: this is a very simple caching scheme that depends on the fact
            // that the fetched ranges tend to be repeated
            const cacheKey = `${refName}:${start}-${end}`
            let f = this.featureCache.get(cacheKey) as VcfFeature[] | undefined

            if (!f) {
                f = await this.getFeaturesAsArray(query, opts)
                this.featureCache.set(cacheKey, f)
            }

            f.forEach(function(v){
                observer.next(v)
            })
            observer.complete()
        }, opts.signal)
    }

    private async getFeaturesAsArray(query: NoAssemblyRegion, opts: BaseOptions = {}) {
        const { refName, start, end } = query
        const { vcf, parser } = await this.configure()
        const features : VcfFeature[] = []

        await vcf.getLines(refName, start, end, {
            lineCallback: (line: string, fileOffset: number) => {
                features.push(
                    new ExtendedVcfFeature({
                        variant: parser.parseLine(line),
                        parser,
                        id: `${this.id}-vcf-${fileOffset}`,
                    })
                )
            },
            ...opts,
        })

        return features
    }
}