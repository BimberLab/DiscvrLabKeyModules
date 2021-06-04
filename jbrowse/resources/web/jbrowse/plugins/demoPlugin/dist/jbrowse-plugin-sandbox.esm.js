import Plugin from '@jbrowse/core/Plugin';
import { isAbstractMenuManager } from '@jbrowse/core/util';
import React, { useState } from 'react';

function _classCallCheck(instance, Constructor) {
  if (!(instance instanceof Constructor)) {
    throw new TypeError("Cannot call a class as a function");
  }
}

function _defineProperties(target, props) {
  for (var i = 0; i < props.length; i++) {
    var descriptor = props[i];
    descriptor.enumerable = descriptor.enumerable || false;
    descriptor.configurable = true;
    if ("value" in descriptor) descriptor.writable = true;
    Object.defineProperty(target, descriptor.key, descriptor);
  }
}

function _createClass(Constructor, protoProps, staticProps) {
  if (protoProps) _defineProperties(Constructor.prototype, protoProps);
  if (staticProps) _defineProperties(Constructor, staticProps);
  return Constructor;
}

function _inherits(subClass, superClass) {
  if (typeof superClass !== "function" && superClass !== null) {
    throw new TypeError("Super expression must either be null or a function");
  }

  subClass.prototype = Object.create(superClass && superClass.prototype, {
    constructor: {
      value: subClass,
      writable: true,
      configurable: true
    }
  });
  if (superClass) _setPrototypeOf(subClass, superClass);
}

function _getPrototypeOf(o) {
  _getPrototypeOf = Object.setPrototypeOf ? Object.getPrototypeOf : function _getPrototypeOf(o) {
    return o.__proto__ || Object.getPrototypeOf(o);
  };
  return _getPrototypeOf(o);
}

function _setPrototypeOf(o, p) {
  _setPrototypeOf = Object.setPrototypeOf || function _setPrototypeOf(o, p) {
    o.__proto__ = p;
    return o;
  };

  return _setPrototypeOf(o, p);
}

function _isNativeReflectConstruct() {
  if (typeof Reflect === "undefined" || !Reflect.construct) return false;
  if (Reflect.construct.sham) return false;
  if (typeof Proxy === "function") return true;

  try {
    Boolean.prototype.valueOf.call(Reflect.construct(Boolean, [], function () {}));
    return true;
  } catch (e) {
    return false;
  }
}

function _assertThisInitialized(self) {
  if (self === void 0) {
    throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
  }

  return self;
}

function _possibleConstructorReturn(self, call) {
  if (call && (typeof call === "object" || typeof call === "function")) {
    return call;
  }

  return _assertThisInitialized(self);
}

function _createSuper(Derived) {
  var hasNativeReflectConstruct = _isNativeReflectConstruct();

  return function _createSuperInternal() {
    var Super = _getPrototypeOf(Derived),
        result;

    if (hasNativeReflectConstruct) {
      var NewTarget = _getPrototypeOf(this).constructor;

      result = Reflect.construct(Super, arguments, NewTarget);
    } else {
      result = Super.apply(this, arguments);
    }

    return _possibleConstructorReturn(this, result);
  };
}

function _slicedToArray(arr, i) {
  return _arrayWithHoles(arr) || _iterableToArrayLimit(arr, i) || _unsupportedIterableToArray(arr, i) || _nonIterableRest();
}

function _arrayWithHoles(arr) {
  if (Array.isArray(arr)) return arr;
}

function _iterableToArrayLimit(arr, i) {
  if (typeof Symbol === "undefined" || !(Symbol.iterator in Object(arr))) return;
  var _arr = [];
  var _n = true;
  var _d = false;
  var _e = undefined;

  try {
    for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) {
      _arr.push(_s.value);

      if (i && _arr.length === i) break;
    }
  } catch (err) {
    _d = true;
    _e = err;
  } finally {
    try {
      if (!_n && _i["return"] != null) _i["return"]();
    } finally {
      if (_d) throw _e;
    }
  }

  return _arr;
}

function _unsupportedIterableToArray(o, minLen) {
  if (!o) return;
  if (typeof o === "string") return _arrayLikeToArray(o, minLen);
  var n = Object.prototype.toString.call(o).slice(8, -1);
  if (n === "Object" && o.constructor) n = o.constructor.name;
  if (n === "Map" || n === "Set") return Array.from(o);
  if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n)) return _arrayLikeToArray(o, minLen);
}

function _arrayLikeToArray(arr, len) {
  if (len == null || len > arr.length) len = arr.length;

  for (var i = 0, arr2 = new Array(len); i < len; i++) arr2[i] = arr[i];

  return arr2;
}

function _nonIterableRest() {
  throw new TypeError("Invalid attempt to destructure non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.");
}

var version = "0.0.1";

function ReactComponent() {
  var _useState = useState(''),
      _useState2 = _slicedToArray(_useState, 2),
      pushed = _useState2[0],
      setPushed = _useState2[1];

  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 50
    }
  }, /*#__PURE__*/React.createElement("h1", null, "Hello plugin developers!"), /*#__PURE__*/React.createElement("button", {
    onClick: function onClick() {
      setPushed('Woah! You pushed the button!');
    }
  }, "Push the button"), /*#__PURE__*/React.createElement("p", null, pushed));
}

var MyProjectPlugin = /*#__PURE__*/function (_Plugin) {
  _inherits(MyProjectPlugin, _Plugin);

  var _super = /*#__PURE__*/_createSuper(MyProjectPlugin);

  function MyProjectPlugin() {
    var _this;

    _classCallCheck(this, MyProjectPlugin);

    _this = _super.apply(this, arguments);
    _this.name = 'MyProject';
    _this.version = version;
    return _this;
  }

  _createClass(MyProjectPlugin, [{
    key: "install",
    value: function install(pluginManager) {
      var jbrequire = pluginManager.jbrequire;
      var types = pluginManager.lib['mobx-state-tree'].types;
      var ViewType = jbrequire('@jbrowse/core/pluggableElementTypes/ViewType');
      var stateModel = types.model({
        type: types.literal('HelloView')
      }).actions(function () {
        return {
          setWidth: function setWidth() {// unused but required by your view
          }
        };
      });
      pluginManager.addViewType(function () {
        return new ViewType({
          name: 'HelloView',
          stateModel: stateModel,
          ReactComponent: ReactComponent
        });
      });
    }
  }, {
    key: "configure",
    value: function configure(pluginManager) {
      if (isAbstractMenuManager(pluginManager.rootModel)) {
        // @ts-ignore
        pluginManager.rootModel.appendToSubMenu(['File', 'Add'], {
          label: 'Open Hello!',
          // @ts-ignore
          onClick: function onClick(session) {
            session.addView('HelloView', {});
          }
        });
      }
    }
  }]);

  return MyProjectPlugin;
}(Plugin);

export default MyProjectPlugin;
//# sourceMappingURL=jbrowse-plugin-sandbox.esm.js.map
