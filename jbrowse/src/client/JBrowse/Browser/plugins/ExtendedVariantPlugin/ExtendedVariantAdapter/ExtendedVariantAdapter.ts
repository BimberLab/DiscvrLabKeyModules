import QuickLRU from '@jbrowse/core/util/QuickLRU';
import { BaseOptions, BaseFeatureDataAdapter } from '@jbrowse/core/data_adapters/BaseAdapter';
import { NoAssemblyRegion } from '@jbrowse/core/util/types';
import { ObservableCreate } from '@jbrowse/core/util/rxjs';
import { Feature } from '@jbrowse/core/util/simpleFeature';
import ExtendedVcfFeature from './ExtendedVcfFeature';
import { VcfFeature } from '@jbrowse/plugin-variants';

export default class extends BaseFeatureDataAdapter {
    protected featureCache = new QuickLRU({ maxSize: 20 })
    private subAdapterP?: Promise<any>

    constructor(...args: any[]) {
      super(...args)

      // Return a Proxy that forwards any unknown member access to the sub-adapter.
      // This avoids re-implementing methods like getHeader/getRefNames/getMetadata/etc.
      const self = this
      return new Proxy(this, {
        get(target, prop, receiver) {
          // If we have it already (e.g., getFeatures, getFeaturesAsArray, BaseFeatureDataAdapter-derived properties), use it directly
          if (prop in target || typeof prop === 'symbol') {
            return Reflect.get(target, prop, receiver)
          }

          // Otherwise, forward to the VcfTabixAdapter sub-adapter
          return async (...callArgs: any[]) => {
            const sub = await self.getVcfSubAdapter()
            const value = (sub as any)[prop]

            // If it’s a method, call it; otherwise return the property value
            if (typeof value === 'function') {
              return value.apply(sub, callArgs)
            }
            return value
          }
        },
      })
    }

    private async getVcfSubAdapter(): Promise<any> {
        if (!this.subAdapterP) {
            const vcfGzLocation = this.getConf('vcfGzLocation')
            const index = this.getConf(['index'])
            const vcfAdapterConf = { type: 'VcfTabixAdapter', vcfGzLocation, index }
            this.subAdapterP = this.getSubAdapter!(vcfAdapterConf)
                .then(({ dataAdapter }) => dataAdapter)
                .catch(e => {
                    this.subAdapterP = undefined
                    throw e
                })
            }
            return this.subAdapterP
    }

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
        }, opts.stopToken)
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

    // Typescript errors at compile time without these stubs
    async configure(opts?: BaseOptions) {
        const sub = await this.getVcfSubAdapter()
        return sub.configure(opts)
    }

    async getRefNames(opts: BaseOptions = {}) {
        const sub = await this.getVcfSubAdapter()
        return sub.getRefNames(opts)
    }

    async getHeader(opts?: BaseOptions) {
        const sub = await this.getVcfSubAdapter()
        return sub.getHeader(opts)
    }

    async getMetadata(opts?: BaseOptions) {
        const sub = await this.getVcfSubAdapter()
        return sub.getMetadata(opts)
    }

    freeResources(): void {
        void this.getVcfSubAdapter().then(sub => sub.freeResources?.())
    }
}