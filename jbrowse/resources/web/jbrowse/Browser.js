define( [
        'dojo/_base/declare',
        'JBrowse/Browser',
        'dijit/form/Button'
    ],
    function(
            declare,
            Browser,
            dijitButton
    ) {
        return declare( [Browser], {

            renderGlobalMenu: function(menuName, args, parent ){
                if (menuName == 'file'){
                    var returnUrl = this.config.returnUrl;
                    if (returnUrl){
                        this.afterMilestone( 'initView', function() {
                            args = dojo.mixin(
                                    {
                                        className: menuName,
                                        innerHTML: '<span class="icon"></span>Return To Site',
                                        onClick: function () {
                                            window.location = returnUrl;
                                        },
                                        id: 'dropdownbutton_' + menuName
                                    },
                                    args || {}
                            );

                            var menuButton = new dijitButton(args);
                            dojo.addClass(menuButton.domNode, 'menu');
                            parent.appendChild(menuButton.domNode);
                        }, this);
                    }
                }

                if (['dataset', 'file'].indexOf(menuName) != -1){
                    return null;
                }

                return this.inherited(arguments);
            }
        });
    }
);