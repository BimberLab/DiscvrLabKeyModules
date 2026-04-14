const devConfig = require('../node_modules/@labkey/build/webpack/dev.config');
const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');

const clientConfig = devConfig;
clientConfig.plugins = constants.processPlugins(entryPoints);
clientConfig.output.publicPath = 'auto';

module.exports = [clientConfig];
