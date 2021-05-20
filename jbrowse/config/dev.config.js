/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

const devConfig = require('../node_modules/@labkey/build/webpack/dev.config')

const entryPoints = require('../src/client/entryPoints');
const constants = require('../node_modules/@labkey/build/webpack/constants');
const webpack = require('webpack');
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

const clientConfig = devConfig

clientConfig.resolve.fallback =
{
    "buffer": require.resolve("buffer"),
    "path": require.resolve("path-browserify"),
    "stream": require.resolve("stream-browserify"),
    "zlib": require.resolve("browserify-zlib"),
    "vm": require.resolve("vm-browserify"),
    "fs": false
}

clientConfig.plugins =
    [new webpack.ProvidePlugin({regeneratorRuntime: 'regenerator-runtime'}),
     new NodePolyfillPlugin()].concat(constants.processPlugins(entryPoints))


module.exports = [clientConfig]