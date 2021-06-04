import Plugin from '@jbrowse/core/Plugin';
import PluginManager from '@jbrowse/core/PluginManager';
export default class MyProjectPlugin extends Plugin {
    name: string;
    version: any;
    install(pluginManager: PluginManager): void;
    configure(pluginManager: PluginManager): void;
}
