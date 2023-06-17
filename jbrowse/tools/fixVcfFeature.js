var fs = require('fs');
var fn = "./node_modules/@jbrowse/plugin-variants/src/VcfTabixAdapter/VcfFeature.ts";

function readWriteAsync() {
    fs.readFile(fn, 'utf-8', function(err, data){
        if (err) throw err;

        if (!data.includes("ts-nocheck")) {
            data = "// @ts-nocheck\n" + data
        }

        fs.writeFile(fn, data, 'utf-8', function (err) {
            if (err) throw err;
        });
    });
}

readWriteAsync();