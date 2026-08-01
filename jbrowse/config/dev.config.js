const config = require('@labkey/build/configs/dev.config');
const { rspack } = require('@rspack/core');

config.output.publicPath = 'auto';

// Bundle production React even in dev builds via nodeEnv. React 19's development
// build allocates a stack-capturing Error per createElement, which makes large lists
// (e.g. the VariantSearch sample multiselect) slow enough to fail Selenium tests.
config.optimization = { ...config.optimization, nodeEnv: 'production' };
config.plugins.push(new rspack.DefinePlugin({
    'process.env': JSON.stringify({ NODE_ENV: 'production' })
}));

module.exports = config;
