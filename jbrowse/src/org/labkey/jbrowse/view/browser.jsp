<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.jbrowse.JBrowseController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%
    /*
     * Copyright (c) 2005-2014 LabKey Corporation
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JBrowseController.BrowserForm form = (JBrowseController.BrowserForm)getModelBean();
    String base = AppProps.getInstance().getContextPath() + "/jbrowseApp/";
    String browserRoot = AppProps.getInstance().getContextPath() + "/jbrowseApp/";
    String dataRoot = AppProps.getInstance().getContextPath() + "/_webdav" + getContainer().getPath() + "/@Files/.jbrowse/" + "databases/" + form.getDatabase();
    Container returnContainer = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
    ActionURL returnUrl = returnContainer.getStartURL(getUser());
%>
<style type="text/css">
    .sectiontitle {
        padding-top: 10px;
    }
</style>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
    <title><%=h(form.getPageTitle())%></title>
    <link rel="stylesheet" type="text/css" href="<%=h(base)%>css/genome.css">

    <script type="text/javascript">
        // jshint unused: false
        var dojoConfig = {
            async: true,
            baseUrl: './src',
            has: {
                'host-node': false // Prevent dojo from being fooled by Electron
            }
        };
        // Move Electron's require out before loading Dojo
        if(window.process&&process.versions&&process.versions.electron) {
            window.electronRequire = require;
            delete window.require;
        }
    </script>
    <script type="text/javascript" src="<%=h(base)%>src/dojo/dojo.js" data-dojo-config="async: 1, baseUrl: '<%=h(base)%>src'"></script>
    <script type="text/javascript" src="<%=h(base)%>src/JBrowse/init.js"></script>

    <script type="text/javascript">
        window.onerror=function(msg){
            if( document.body )
                document.body.setAttribute("JSError",msg);
        }

        // puts the main Browser object in this for convenience.  feel
        // free to move it into function scope if you want to keep it
        // out of the global namespace
        var JBrowse;
        require(['<%=h(AppProps.getInstance().getContextPath())%>/jbrowse/Browser.js', 'dojo/io-query', 'dojo/json' ],
                function (Browser,ioQuery,JSON) {
                    // the initial configuration of this JBrowse
                    // instance

                    // NOTE: this initial config is the same as any
                    // other JBrowse config in any other file.  this
                    // one just sets defaults from URL query params.
                    // If you are embedding JBrowse in some other app,
                    // you might as well just set this initial config
                    // to something like { include: '../my/dynamic/conf.json' },
                    // or you could put the entire
                    // dynamically-generated JBrowse config here.

                    // parse the query vars in the page URL
                    var queryParams = ioQuery.queryToObject( window.location.search.slice(1) );

                    var config = {
                        containerID: "GenomeBrowser",
                        baseUrl: "<%=h(base)%>",
                        browserRoot: <%=PageFlowUtil.jsString(browserRoot)%>,
                        dataRoot: <%=PageFlowUtil.jsString(dataRoot)%>,
                        queryParams: queryParams,
                        location: queryParams.loc,
                        forceTracks: queryParams.tracks,
                        initialHighlight: queryParams.highlight,
                        show_nav: queryParams.nav,
                        show_tracklist: queryParams.tracklist,
                        show_overview: queryParams.overview,
                        show_menu: queryParams.menu,
                        classicMenu: false,
                        show_tracklabels: queryParams.tracklabels,
                        highResolutionMode: queryParams.highres,
                        returnUrl: '<%=h(returnUrl.toString())%>',
                        aboutThisBrowser: {
                            title: <%=q(form.getPageTitle())%>
                        },
                        plugins: {
                            AnnotatedVariants: {
                                location: '<%=h(AppProps.getInstance().getContextPath())%>/jbrowse/plugins/AnnotatedVariants',
                                css: '<%=h(AppProps.getInstance().getContextPath())%>/jbrowse/plugins/AnnotatedVariants/css'
                            }
                        },
                        stores: { url: { type: "JBrowse/Store/SeqFeature/FromConfig", features: [] } },
                        makeFullViewURL: function( browser ) {

                            // the URL for the 'Full view' link
                            // in embedded mode should be the current
                            // view URL, except with 'nav', 'tracklist',
                            // and 'overview' parameters forced to 1.

                            return browser.makeCurrentViewURL({ nav: 1, tracklist: 1, overview: 1 });
                        },
                        updateBrowserURL: true
                    };

                    //if there is ?addFeatures in the query params,
                    //define a store for data from the URL
                    if( queryParams.addFeatures ) {
                        config.stores.url.features = JSON.parse( queryParams.addFeatures );
                    }

                    // if there is ?addTracks in the query params, add
                    // those track configurations to our initial
                    // configuration
                    if( queryParams.addTracks ) {
                        config.tracks = JSON.parse( queryParams.addTracks );
                    }

                    // if there is ?addStores in the query params, add
                    // those store configurations to our initial
                    // configuration
                    if( queryParams.addStores ) {
                        config.stores = JSON.parse( queryParams.addStores );
                    }

                    // create a JBrowse global variable holding the JBrowse instance
                    JBrowse = new Browser( config );
                });
    </script>

</head>

<div id="GenomeBrowser" style="height: 100%; width: 100%; padding: 0; border: 0;"></div>
<div style="display: none">JBrowseDefaultMainPage</div>