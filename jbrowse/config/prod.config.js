/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

const prodConfig = require('../node_modules/@labkey/build/config/prod.config')

const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/config/constants');

// TODO: this is probably no longer required
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

const clientConfig = prodConfig

// TODO: this is probably no longer required
// See: https://stackoverflow.com/questions/68707553/uncaught-referenceerror-buffer-is-not-defined
clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer")
}

clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer")
}

// TODO: this might also not be needed
clientConfig.plugins = [new NodePolyfillPlugin()].concat(constants.processPlugins(entryPoints))

clientConfig.output.publicPath = 'auto'

module.exports = [clientConfig]