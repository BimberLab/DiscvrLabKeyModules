/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var SOURCE_DIR = 'node_modules/jbrowse/';
var OUTPUT_DIR = 'resources/web/jbrowseApp/';

var fs = require('fs');
var fsex = require('fs-extra');

if (!fs.existsSync(OUTPUT_DIR)){
    fs.mkdirSync(OUTPUT_DIR);
}

console.log('copying JBrowse App');
fsex.copy(SOURCE_DIR, OUTPUT_DIR, function (err) {
    if (err) return console.error(err);
    console.log('complete');

    console.log('replacing main.css');
    var ORIG_CSS = OUTPUT_DIR + 'css/main.css';
    var REPLACEMENT_CSS = 'webpack/main.css';
    if (fs.existsSync(ORIG_CSS)){
        fs.unlinkSync(ORIG_CSS);
    }

    fs.writeFileSync(ORIG_CSS, fs.readFileSync(REPLACEMENT_CSS));
});