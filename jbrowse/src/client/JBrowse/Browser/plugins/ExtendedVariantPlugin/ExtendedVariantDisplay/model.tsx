import { ConfigurationReference, getConf } from '@jbrowse/core/configuration';
import { AnyConfigurationModel } from '@jbrowse/core/configuration';
import { getContainingTrack, getContainingView, getSession } from '@jbrowse/core/util';
import FilterListIcon from '@mui/icons-material/FilterList';
import VisibilityIcon from '@mui/icons-material/Visibility';
import configSchemaF from './configSchema';
import { getEnv, IAnyStateTreeNode, types } from '@jbrowse/mobx-state-tree';
import PaletteIcon from '@mui/icons-material/Palette';
import { LinearGenomeViewModel } from '@jbrowse/plugin-linear-genome-view';
import { navigateToSearch, navigateToTable } from '../../../../utils';
import SerializableFilterChain from '@jbrowse/core/pluggableElementTypes/renderers/util/serializableFilterChain';

function escapeForSingleQuotedJexl(value: string) {
   return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

function getContainingTrackWithConfig(node: IAnyStateTreeNode): IAnyStateTreeNode & { configuration: AnyConfigurationModel } {
   return getContainingTrack(node) as any;
}

export default jbrowse => {
   const configSchema = jbrowse.jbrequire(configSchemaF)
   const { BaseLinearDisplay } = jbrowse.getPlugin(
      'LinearGenomeViewPlugin',
   ).exports

   return types
      .compose(
         'ExtendedVariantDisplay',
         BaseLinearDisplay,
         types.model({
            type: types.literal('ExtendedVariantDisplay'),
            trackShowLabels: types.maybe(types.boolean),
            trackDisplayMode: types.maybe(types.string),
            trackMaxHeight: types.maybe(types.number),
            configuration: ConfigurationReference(configSchema),
         }),
      )
      .actions(self => ({
         setDisplayMode(val: string) {
            self.trackDisplayMode = val
            if (val === 'collapse' && self.showLabels) {
               self.toggleShowLabels()
            }
         },

         setMaxHeight(val: number) {
            self.trackMaxHeight = val
         },

         toggleShowLabels() {
            self.trackShowLabels = !self.showLabels
         },

         selectFeature(feature){
            const session = getSession(self)
            const track = getContainingTrackWithConfig(self)

            // @ts-ignore
            const trackId = getConf(track, ['trackId'])
            const detailsConfig = getConf(track, ['displays', '0', 'detailsConfig'])

            const widgetId = 'Variant-' + trackId;
            const featureWidget = session.addWidget(
               'ExtendedVariantWidget',
               widgetId,
               {
                  featureData: feature,
                  trackId: trackId,
                  message: '',
                  detailsConfig: detailsConfig
               }
            )

            session.showWidget(featureWidget)
            session.setSelection(feature)
         },
      }))

      .views(self => {
         const { renderProps: superRenderProps } = self
         const filterMenu = {
            label: 'Filter By Attributes',
            icon: FilterListIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrackWithConfig(self)
               const widgetId = 'InfoFilterWidget-' + getConf(track, 'trackId');
               const filterWidget = session.addWidget(
                  'InfoFilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(filterWidget)
            }
         }
         const colorMenu = {
            label: "Color Selection",
            icon: PaletteIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrackWithConfig(self)
               const widgetId = 'ColorWidget-' + getConf(track, 'trackId');
               const colorWidget = session.addWidget(
                  'ColorWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(colorWidget)
            }
         }

         const sampleFilterMenu = {
            label: 'Filter By Sample',
            icon: FilterListIcon,
            onClick: () => {
               const session = getSession(self)
               const track = getContainingTrackWithConfig(self)
               const widgetId = 'SampleFilterWidget-' + getConf(track, 'trackId');
               const sampleFilterWidget = session.addWidget(
                  'SampleFilterWidget',
                  widgetId,
                  { track: track.configuration }
               )
               session.showWidget(sampleFilterWidget)
            }
         }

         return {
            renderProps() {
               const config = self.rendererConfig
               return {
                  ...superRenderProps(),
                  config: config,
                  rendererConfig: config,
                  filters: new SerializableFilterChain({
                     filters: this.activeFilters(),
                     jexl: jbrowse.jexl,
                  }),
               }
            },

            activeFilters() {
               const staticJexlFilters = (getConf(self, 'jexlFilters') || []).map((f: string) =>
                  f?.startsWith('jexl:') ? f : `jexl:${f}`,
               )

               const infoFilters = getConf(self, 'infoFilters') || []
               const activeSamples = getConf(self, 'activeSamples') || ''
               const sampleFilters = activeSamples
                  ? activeSamples
                       .split(',')
                       .map((s: string) => s.trim())
                       .filter((s: string) => !!s)
                  : []

               const dynamicFilters: string[] = []

               if (infoFilters.length) {
                  const serialized = escapeForSingleQuotedJexl(JSON.stringify(infoFilters))
                  dynamicFilters.push(`jexl:passesInfoFilters(feature,'${serialized}')`)
               }

               if (sampleFilters.length) {
                  const serialized = escapeForSingleQuotedJexl(JSON.stringify(sampleFilters))
                  dynamicFilters.push(`jexl:passesSampleFilters(feature,'${serialized}')`)
               }

               return [...staticJexlFilters, ...dynamicFilters]
            },

            get rendererTypeName() {
               return self.configuration.renderer.type
            },

            get rendererConfig() {
               const configBlob = getConf(self, ['renderer']) || {}

               return self.rendererType.configSchema.create(
                       {
                          ...configBlob,
                          showLabels: this.showLabels,
                          displayMode: this.displayMode,
                          maxHeight: this.maxHeight
                       },
                       getEnv(self),
               )
            },

            get showLabels() {
               const showLabels = getConf(self, ['renderer', 'showLabels'])
               return self.trackShowLabels !== undefined
                       ? self.trackShowLabels
                       : showLabels
            },

            get maxHeight() {
               const maxHeight = getConf(self, ['renderer', 'maxHeight'])
               return self.trackMaxHeight !== undefined
                       ? self.trackMaxHeight
                       : maxHeight
            },

            get displayMode() {
               const displayMode = getConf(self, ['renderer', 'displayMode'])
               return self.trackDisplayMode !== undefined
                       ? self.trackDisplayMode
                       : displayMode
            },

            trackMenuItems() {
               const buttons = [filterMenu, sampleFilterMenu, colorMenu, {
                  label: 'Show labels',
                  icon: VisibilityIcon,
                  type: 'checkbox',
                  checked: self.showLabels,
                  onClick: () => {
                     self.toggleShowLabels()
                  }
               }, {
                  label: 'Display mode',
                  icon: VisibilityIcon,
                  subMenu: [
                     'compact',
                     'reducedRepresentation',
                     'normal',
                     'collapse',
                  ].map(val => ({
                     label: val,
                     onClick: () => {
                        self.setDisplayMode(val)
                     },
                  })),
               }, {
                  label: 'View As Table',
                  onClick: () => {
                     const track = getContainingTrackWithConfig(self)
                     const view = getContainingView(self) as LinearGenomeViewModel

                     const region = view.getSelectedRegions(undefined, undefined)[0]
                     const location = region.refName + ':' + (1+region.start) + '..' + (1+region.end)
                     const sessionId = view.id;
                     navigateToTable(sessionId, location, track.configuration.trackId, track)
                  }
               }]

               const supportsLuceneIndex = getConf(self, ['renderer', 'supportsLuceneIndex'])
               if (supportsLuceneIndex) {
                  buttons.push({
                     label: 'Variant Search',
                     onClick: () => {
                        const track = getContainingTrackWithConfig(self)
                        const view = getContainingView(self) as LinearGenomeViewModel

                        const region = view.getSelectedRegions(undefined, undefined)[0]
                        const location = region.refName + ':' + (1+region.start) + '..' + (1+region.end)
                        const sessionId = view.id;
                        navigateToSearch(sessionId, location, track.configuration.trackId, null, track)
                     }
                  })
               }

               return buttons
            }
         }
      })
}