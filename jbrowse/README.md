# Current Implementation

This branch is a temporary solution and should not be used for serious use as it breaks good code practice and may cause long-term development issues. The code in this branch serves more as a record for reference during  development of a proper solution.

## Instructions
1. Clone repo, checkout 21.4-jbrowse2-2.1.0 branch
2. In jbrowse, run "npm install"
3. Replace the contents of jbrowse/node_modules/@labkey/build/webpack/dev.config.js with that of jbrowse/dev.config.js
4. In jbrowse, run "npm run build"
5. Run debug, page is visible at http://localhost:8080/labkey/home/jbrowse-jbrowse.view?