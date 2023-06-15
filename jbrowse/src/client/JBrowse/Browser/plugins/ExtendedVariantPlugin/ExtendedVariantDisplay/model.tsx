import { ConfigurationReference, getConf } from '@jbrowse/core/configuration';
import { AnyConfigurationModel, } from '@jbrowse/core/configuration/configurationSchema';
import { getContainingTrack, getContainingView, getSession } from '@jbrowse/core/util';
import FilterListIcon from '@material-ui/icons/FilterList';
import VisibilityIcon from '@material-ui/icons/Visibility';
import configSchemaF from './configSchema';
import { getEnv, IAnyStateTreeNode, types } from 'mobx-state-tree';
import PaletteIcon from '@material-ui/icons/Palette';
import { default as SetMaxHeightDlg } from '@jbrowse/plugin-linear-genome-view/src/LinearBasicDisplay/components/SetMaxHeight';
import { LinearGenomeViewModel } from '@jbrowse/plugin-linear-genome-view';
import { navigateToTable } from '../../../../utils';

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

            const trackId = getConf(track, 'trackId')
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
               const widgetId = 'Variant-' + getConf(track, 'trackId');
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
               const widgetId = 'Variant-' + getConf(track, 'trackId');
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
               const widgetId = 'Variant-' + getConf(track, 'trackId');
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
                  rendererConfig: config
               }
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
               return [
                  filterMenu, sampleFilterMenu, colorMenu,
                  {
                     label: 'Show labels',
                     icon: VisibilityIcon,
                     type: 'checkbox',
                     checked: self.showLabels,
                     onClick: () => {
                        self.toggleShowLabels()
                     }
                  },
                  {
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
                  },
                  {
                     label: 'Set max height',
                     onClick: () => {
                        getSession(self).queueDialog((doneCallback: Function) => [
                           SetMaxHeightDlg,
                           { model: self, handleClose: doneCallback },
                        ])
                     },
                  },
                  {
                     label: 'View As Table',
                     onClick: () => {
                        const track = getContainingTrackWithConfig(self)
                        const view = getContainingView(self) as LinearGenomeViewModel
                        
                        const region = view.getSelectedRegions(undefined, undefined)[0]
                        const location = region.refName + ':' + region.start + '..' + region.end
                        const sessionId = view.id;
                        navigateToTable(sessionId, location, track.configuration.trackId, track)
                     },
                  }
              ]
            }
         }
      })
}