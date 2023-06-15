var fs = require('fs');
var fn = "./node_modules/@jbrowse/plugin-variants/src/VcfTabixAdapter/VcfFeature.ts";

function readWriteAsync() {
    fs.readFile(fn, 'utf-8', function(err, data){
        if (err) throw err;

        console.log('edit started');

        data = "// @ts-nocheck\n" + data

        fs.writeFile(fn, data, 'utf-8', function (err) {
            if (err) throw err;
            console.log('edit complete');
        });
    });
}

readWriteAsync();

// function readWriteSync() {
//     var data = fs.readFileSync('filelist.txt', 'utf-8');
//
//     var newValue = data.replace(/^\./gim, 'myString');
//
//     fs.writeFileSync('filelistSync.txt', newValue, 'utf-8');
//
//     console.log('readFileSync complete');
// }
//
//
// readWriteSync();
//
