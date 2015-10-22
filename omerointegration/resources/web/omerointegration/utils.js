Ext4.ns('OMERO.Utils');

OMERO.Utils = new function(){

    return {
        renderViewer: function(viewerUrl){
            Ext4.create('Ext.Window', {
                title : 'OMERO Viewer',
                //width : 300,
                //height: 300,
                layout : 'fit',
                modal: true,
                items : [{
                    xtype : 'component',
                    autoEl : {
                        tag : 'iframe',
                        src : viewerUrl
                    }
                }],
                initEvents: function () {
                    //make sure your window is rendered and have sizes and position
                    if(!this.rendered) {
                        this.on('afterrender', this.initEvents, this, {single: true});
                        return;
                    }

                    this.mon(Ext4.getBody(), 'click', this.checkCloseClick, this);
                },
                checkCloseClick: function (event) {
                    var cx = event.getX(), cy = event.getY(),
                            box = this.getBox();

                    if (cx < box.x || cx > box.x + box.width || cy < box.y || cy > box.y + box.height) {
                        //clean up listener listener
                        this.mun(Ext4.getBody(), 'click', this._checkCloseClick, this);
                        this.close();
                    }
                }
            }).show();
        }
    }
}