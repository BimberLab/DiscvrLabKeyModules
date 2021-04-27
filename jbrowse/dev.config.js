/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

 // COPY THIS FILE TO node_modules/@labkey/build/webpack/dev.config.js
const lkModule = process.env.LK_MODULE;
const entryPoints = require('../../../../src/client/entryPoints');
const constants = require('./constants');
const webpack = require('webpack');

// set based on the lk module calling this config
__dirname = lkModule;

module.exports = {
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
        rules: constants.loaders.TYPESCRIPT_LOADERS.concat(constants.loaders.STYLE_LOADERS)
    },

    resolve: {
        extensions: constants.extensions.TYPESCRIPT
    },

    plugins: [new webpack.ProvidePlugin({
        regeneratorRuntime: 'regenerator-runtime',
    })],

    node: {
        fs: "empty"
    },
};
