const watchConfig = require('../node_modules/@labkey/build/webpack/watch.config');
const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');

const clientConfig = watchConfig;
clientConfig.plugins = constants.processPlugins(entryPoints);
clientConfig.output.publicPath = 'auto';

module.exports = [clientConfig];
