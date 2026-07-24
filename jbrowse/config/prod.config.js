/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

const prodConfig = require('../node_modules/@labkey/build/webpack/prod.config')

const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

const clientConfig = prodConfig

// See: https://stackoverflow.com/questions/68707553/uncaught-referenceerror-buffer-is-not-defined
clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer")
}

clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer")
}

clientConfig.plugins = [new NodePolyfillPlugin()].concat(constants.processPlugins(entryPoints))

clientConfig.output.publicPath = 'auto'

module.exports = [clientConfig]