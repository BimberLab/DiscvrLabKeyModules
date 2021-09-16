import { BaseOptions } from '@jbrowse/core/data_adapters/BaseAdapter'
import { NoAssemblyRegion } from '@jbrowse/core/util/types'
import { ObservableCreate } from '@jbrowse/core/util/rxjs'
import { Feature } from '@jbrowse/core/util/simpleFeature'
import ExtendedVcfFeature from './VcfFeature'
import { default as VcfTabixAdapter } from '@jbrowse/plugin-variants/src/VcfTabixAdapter/VcfTabixAdapter'

export default class extends VcfTabixAdapter{
  public getFeatures(query: NoAssemblyRegion, opts: BaseOptions = {}) {
    return ObservableCreate<Feature>(async observer => {
      const { refName, start, end } = query
      const { vcf, parser } = await this.configure()
      await vcf.getLines(refName, start, end, {
        lineCallback: (line: string, fileOffset: number) => {
          observer.next(
            new ExtendedVcfFeature({
              variant: parser.parseLine(line),
              parser,
              id: `${this.id}-vcf-${fileOffset}`,
            }),
          )
        },
        ...opts,
      })
      observer.complete()
    }, opts.signal)
  }
}