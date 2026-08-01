const config = require('@labkey/build/configs/watch.config');
const { rspack } = require('@rspack/core');

config.plugins.push(new rspack.DefinePlugin({
    'process.env': JSON.stringify({ NODE_ENV: 'development' })
}));

module.exports = config;
