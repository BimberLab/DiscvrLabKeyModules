// This script is a workaround until pkg supports ES modules: https://github.com/vercel/pkg/issues/1291
// See also: https://nodejs.org/api/single-executable-applications.html
// This should be replaced once a better solution is available
const fs = require('fs');
const { exec, execSync } = require('child_process');

function convertEs6ToCommonJS(packageName) {
    const sourceFile = './buildCli/node_modules/@isaacs/cliui/node_modules/' + packageName + '/index.js';
    const origFile = sourceFile + '.orig';
    if (fs.existsSync(origFile)) {
        console.log('File exists, skipping: ' + origFile);
    } else {
        console.log('Updating: ' + sourceFile);
        if (!fs.existsSync(sourceFile)) {
            throw new Error('Missing file: ' + sourceFile);
        }

        if (fs.existsSync('./buildCli/' + packageName)) {
            console.log('Deleting existing index.js file')
            fs.unlinkSync('./buildCli/' + packageName)
        }

        try {
            execSync('npx esbuild --outdir=./buildCli/' + packageName + ' --target=es6 --format=cjs ' + sourceFile)
        } catch (err) {
            throw new Error('ERROR: ' + err.message);
        }

        try {
            fs.renameSync(sourceFile, origFile)
        } catch (err){
            throw new Error('ERROR: ' + err);
        }

        try {
            fs.renameSync('./buildCli/' + packageName + '/index.js', sourceFile)
        } catch (err){
            throw new Error('ERROR: ' + err);
        }
    }
}

convertEs6ToCommonJS('ansi-regex')
convertEs6ToCommonJS('ansi-styles')
convertEs6ToCommonJS('strip-ansi')
convertEs6ToCommonJS('wrap-ansi')
convertEs6ToCommonJS('string-width')

const child = exec('npx pkg --outdir=./resources/external/jb-cli ./buildCli/node_modules/@jbrowse/cli', (err, stdout, stderr) => {
    if (err) {
        throw new Error('Unable to run pkg: ' + err)
    }
});

child.stdout.setEncoding('utf8');
child.stdout.on('data', function(data) {
    console.log(data);
});

child.stderr.setEncoding('utf8');
child.stderr.on('data', function(data) {
    console.log(data);
});

child.on('close', function(code) {
    console.log('closing pkg: ' + code);
});

