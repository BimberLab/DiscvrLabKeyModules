const config = require('@labkey/build/configs/prod.config');
const { rspack } = require('@rspack/core');

config.output.publicPath = 'auto';
config.plugins.push(new rspack.DefinePlugin({
    'process.env': JSON.stringify({ NODE_ENV: 'production' })
}));

module.exports = config;
