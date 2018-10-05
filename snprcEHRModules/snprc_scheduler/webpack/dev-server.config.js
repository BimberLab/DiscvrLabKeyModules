require("babel-polyfill");
const path = require("path");
const webpack = require("webpack");

module.exports = {
    context: path.resolve(__dirname, '..'),
    mode: "development",
    devtool: 'eval',
    devServer: {
      inline:true,
      port: 8081
    },
    entry: {
        'app': ['./src/client/app.js']
    },
    output: {
        path: path.resolve(__dirname, '../resources/web/snprc_scheduler/app/'),
        publicPath: 'http://localhost:8081/',
        filename: '[name].js'
    },
    externals: {
        'react/addons': true,
        'react/lib/ExecutionEnvironment': true,
        'react/lib/ReactContext': true
    },
    module: {
        rules: [{
            test: /\.jsx?$/,
            loaders: ['babel-loader',]
        },
        {
            test: /\.css$/,   
            loaders: ['style-loader', 'css-loader'],
       }]
    },
    plugins: [
        new webpack.NoEmitOnErrorsPlugin(),
        new webpack.HotModuleReplacementPlugin()
    ],
    resolve: {
        extensions: [ '.jsx', '.js', '.tsx', '.ts' ]
    }
};