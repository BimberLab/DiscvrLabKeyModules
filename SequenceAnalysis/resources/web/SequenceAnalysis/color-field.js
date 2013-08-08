/*
* Copyright (c) <2011> <Ryan Petrello>
*
* Permission is hereby granted, free of charge, to any person obtaining a copy of
* this software and associated documentation files (the "Software"), to deal in
* the Software without restriction, including without limitation the rights to
* use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
* of the Software, and to permit persons to whom the Software is furnished to do
* so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

/*
    Source: http://ryanpetrello.com/ext-ux/ColorField/
    Consider: Looks like a newer version of this has been posted.
 */

/**
 * @class Ext.ux.ColorField
 * @extends Ext.form.TriggerField
 * Provides a color input field with a {@link Ext.ColorPalette} dropdown.
* @constructor
* Create a new ColorField
 * <br />Example:
 * <pre><code>
var color_field = new Ext.ux.ColorField({
	fieldLabel: 'Color',
	id: 'color',
	width: 175,
	allowBlank: false
});
</code></pre>
* @param {Object} config
 */

Ext.ux.ColorField = Ext.extend(Ext.form.TriggerField,  {
    /**
     * @cfg {String} invalidText
     * The error to display when the color in the field is invalid (defaults to
     * '{value} is not a valid color - it must be in the format {format}').
     */
    invalidText : "'{0}' is not a valid color - it must be in a the hex format (# followed by 3 or 6 letters/numbers 0-9 A-F)",
    /**
     * @cfg {String} triggerClass
     * An additional CSS class used to style the trigger button.  The trigger will always get the
     * class 'x-form-trigger' and triggerClass will be <b>appended</b> if specified (defaults to 'x-form-color-trigger'
     * which displays a color wheel icon).
     */
    triggerClass : 'x-form-color-trigger',
    /**
     * @cfg {String/Object} autoCreate
     * A DomHelper element spec, or true for a default element spec (defaults to
     * {tag: "input", type: "text", size: "10", autocomplete: "off"})
     */

    // private
    defaultAutoCreate : {tag: "input", type: "text", size: "10", maxlength: "7", autocomplete: "off"},

    // Limit input to hex values
	maskRe: /[#a-f0-9]/i,
	
    // private
    validateValue : function(value){
        if(!Ext.ux.ColorField.superclass.validateValue.call(this, value)){
            return false;
        }
        if(value.length < 1){ // if it's blank and textfield didn't flag it then it's valid
        	 this.setColor('');
        	 return true;
        }

        var parseOK = this.parseColor(value);

        if(!value || (parseOK == false)){
            this.markInvalid(String.format(this.invalidText,value));
            return false;
        }
        //Added by BNB to avoid unnecessarily reseting background
		if(this.getValue() != value)
            this.setColor(value);
        return true;
    },

	/**
	 * Sets the current color and changes the background.
	 * Does *not* change the value of the field.
	 * @param {String} hex The color value.
	 */
	setColor : function(color) {
		if (color=='' || color==undefined)
		{
			if (this.emptyText!='' && this.parseColor(this.emptyText))
				color=this.emptyText;
			else
				color='transparent';
		}

        if (this.el)
			this.el.setStyle( {
				'background-color': color,
                'color': color,
                'background-image': 'none'
			});
		else
		{
			this.on('render',function(){this.setColor(color)},this);
		}
	},
	
    // private
    // Provides logic to override the default TriggerField.validateBlur which just returns true
    validateBlur : function(){
        return !this.menu || !this.menu.isVisible();
    },

    /**
     * Returns the current value of the color field
     * @return {String} value The color value
     */
    getValue : function(){
        return Ext.ux.ColorField.superclass.getValue.call(this) || "";
    },

    /**
     * Sets the value of the color field.  You can pass a string that can be parsed into a valid HTML color
     * <br />Usage:
     * <pre><code>
		colorField.setValue('#FFFFFF');
       </code></pre>
     * @param {String} color The color string
     */
    setValue : function(color){
        Ext.ux.ColorField.superclass.setValue.call(this, this.formatColor(color));
		this.setColor( this.formatColor(color));
    },

    // private
    parseColor : function(value){
		return (!value || (value.substring(0,1) != '#')) ?
			false : (value.length==4 || value.length==7 );
    },

    // private
    formatColor : function(value){
		if (!value || this.parseColor(value))
			return value;
		if (value.length==3 || value.length==6) {
			return '#' + value;
		}
        return '';
    },

    // private
    menuListeners : {
        select: function(e, c){
            this.setValue(c);
        },
        show : function(){ // retain focus styling
            this.onFocus();
        },
        hide : function(){
            this.focus.defer(10, this);
            var ml = this.menuListeners;
            this.menu.un("select", ml.select,  this);
            this.menu.un("show", ml.show,  this);
            this.menu.un("hide", ml.hide,  this);
        }
    },

    // private
    // Implements the default empty TriggerField.onTriggerClick function to display the ColorPalette
    onTriggerClick : function(){
        if(this.disabled){
            return;
        }
        if(this.menu == null){
            this.menu = new Ext.menu.ColorMenu();
        }

        this.menu.on(Ext.apply({}, this.menuListeners, {
            scope:this
        }));

        this.menu.show(this.el, "tl-bl?");
    }
});

Ext.reg('colorfield',Ext.ux.ColorField);
