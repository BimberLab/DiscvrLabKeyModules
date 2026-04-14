const prodConfig = require('../node_modules/@labkey/build/webpack/prod.config');
const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');

const clientConfig = prodConfig;
clientConfig.plugins = constants.processPlugins(entryPoints);
clientConfig.output.publicPath = 'auto';

module.exports = [clientConfig];
