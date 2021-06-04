
'use strict'

if (process.env.NODE_ENV === 'production') {
  module.exports = require('./jbrowse-plugin-sandbox.cjs.production.min.js')
} else {
  module.exports = require('./jbrowse-plugin-sandbox.cjs.development.js')
}
