import { ConfigurationSchema } from '@jbrowse/core/configuration';
import { createBaseTrackConfig } from '@jbrowse/core/pluggableElementTypes/models';
import PluginManager from '@jbrowse/core/PluginManager';

export default function createExtendedVariantTrackConfig(pluginManager: PluginManager) {
    return ConfigurationSchema(
        'ExtendedVariantTrack',
        { },
        {
            baseConfiguration: createBaseTrackConfig(pluginManager),
            explicitIdentifier: 'trackId',
        },
    )
}