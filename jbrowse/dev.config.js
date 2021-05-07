/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
 // COPY THIS FILE TO node_modules/@labkey/build/webpack/dev.config.js
const lkModule = process.env.LK_MODULE;
const entryPoints = require('./src/client/entryPoints');
const constants = require('./node_modules/@labkey/build/webpack/constants');
const webpack = require('webpack');
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

// set based on the lk module calling this config
__dirname = lkModule;

const clientConfig = {

    context: constants.context(__dirname),

    mode: 'development',

    devtool: 'eval',

    entry: constants.processEntries(entryPoints),

    output: {
        path: constants.outputPath(__dirname),
        publicPath: './', // allows context path to resolve in both js/css
        filename: '[name].js'
    },

    module: {
        rules: constants.loaders.TYPESCRIPT.concat(constants.loaders.STYLE).concat(constants.loaders.FILES),
    },

    resolve: {
        alias: constants.aliases.LABKEY_PACKAGES,
        extensions: constants.extensions.TYPESCRIPT,
        fallback: {
            "buffer": require.resolve("buffer"),
            "path": require.resolve("path-browserify"),
            "stream": require.resolve("stream-browserify"),
            "zlib": require.resolve("browserify-zlib"),
            "vm": require.resolve("vm-browserify"),
            "fs": false
        }
    },

      plugins: [new webpack.ProvidePlugin({
            regeneratorRuntime: 'regenerator-runtime'
        }), new NodePolyfillPlugin()].concat(constants.processPlugins(entryPoints)),
}

module.exports = [clientConfig]