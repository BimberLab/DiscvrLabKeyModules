/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

const watchConfig = require('../node_modules/@labkey/build/webpack/watch.config')

const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

const clientConfig = watchConfig

clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer"),
}

clientConfig.plugins = [new NodePolyfillPlugin()].concat(constants.processPlugins(entryPoints))

clientConfig.output.publicPath = 'auto'

module.exports = [clientConfig]