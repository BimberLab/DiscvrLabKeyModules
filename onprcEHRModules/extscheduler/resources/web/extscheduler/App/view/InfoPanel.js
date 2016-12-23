
Ext.define('App.view.InfoPanel', {
    extend    : 'Ext.Container',
    alias     : 'widget.infopanel',
    requires  : [
        'App.view.InfoPanelModel'
    ],
    viewModel : 'infopanel',
    reference : 'infopanel',
    cls       : 'infopanel',
    width     : 340,
    layout    : {
        type  : 'vbox',
        align : 'stretch'
    },
    items : [
        {
            xtype : 'eventform',
            title : 'Event details',
            editable : false
        },
        {
            xtype       : 'panel',
            title       : 'Resources',
            cls         : 'resources',
            bodyPadding : '0 15',
            flex        : 1,
            layout      : 'fit',
            margin      : '10 0 0 0',
            items       : [{ xtype : 'resources' }]
        }
    ]
});