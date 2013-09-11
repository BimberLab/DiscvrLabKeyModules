/**
 *
 */
Ext4.define('Laboratory.button.IconButton', {
    extend: 'LDK.button.LinkButton',
    alias: 'widget.laboratory-iconbutton',

    renderTpl: [
        '<div id="{id}-wrap" class="tool-icon" style="margin-left:auto;margin-right:auto;">',
        '<a ' +
        '<tpl if="href">href="{href}"</tpl>' +
        '<tpl if="!href">href="javascript:void(0)"</tpl>' +
        '<tpl if="tooltip"> data-qtip="{tooltip}"</tpl>' +
            '>' +
            '<tpl if="icon"><div><img id="{id}-btnIconEl" src="{icon}"/ ></div></tpl>' +
            '{text}</a>',
        '</div>'
    ],
    renderSelectors: {
        linkEl: 'a'
    },

    initComponent: function() {
        this.callParent(arguments);

        Ext4.apply(this.renderData, {
            icon: this.icon
        });
    }

});