const HtmlWebPackPlugin = require("html-webpack-plugin");
const path = require("path");
const webpack = require("webpack");

module.exports = {
    mode: "development",
    output: {
        path: path.join(__dirname, '../webpack-build'),
        filename: 'bundle.js',
        publicPath: '/'
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
    }
};