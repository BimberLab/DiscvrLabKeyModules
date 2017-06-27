Ext4.define('JBrowse.window.GenomeBrowserHelpWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(event) {
            Ext4.create('JBrowse' +
                    '.window.GenomeBrowserHelpWindow').show(event.target);
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Genome Browser Help',
            width: 1010,
            minHeight: 390,
            buttonAlign: 'left',
            modal: true,
            items: [{
                itemId: 'card',
                layout: 'card',
                defaults: {
                    xtype: 'image',
                    width: 1000,
                    height: 340
                },
                items: [{
                    src: LABKEY.ActionURL.getContextPath() + '/jbrowse/img/img0.jpg',
                    active: true
                },{
                    src: LABKEY.ActionURL.getContextPath() + '/jbrowse/img/img1.jpg'
                },{
                    src: LABKEY.ActionURL.getContextPath() + '/jbrowse/img/img2.jpg'
                },{
                    src: LABKEY.ActionURL.getContextPath() + '/jbrowse/img/img3.jpg'
                },{
                    src: LABKEY.ActionURL.getContextPath() + '/jbrowse/img/img4.jpg'
                }]
            }],
            buttons: [{
                id: 'move-prev',
                text: 'Back',
                scope: this,
                handler: function(btn) {
                    this.navigate('prev');
                },
                disabled: true
            },'->', {
                id: 'move-next',
                text: 'Next',
                scope: this,
                handler: function(btn) {
                    this.navigate('next');
                }
            }],
            listeners: {
                show: function(){

                }
            }
        });

        this.callParent();

        this.mon(Ext4.getBody(), 'click', function(el, e) {
            this.close();
        }, this, {delegate: '.x4-mask'});
    },

    navigate: function(direction){
        var panel = this.down('#card');
        var layout = panel.getLayout();
        layout[direction]();
        Ext4.getCmp('move-prev').setDisabled(!layout.getPrev());
        Ext4.getCmp('move-next').setDisabled(!layout.getNext());
    }
});