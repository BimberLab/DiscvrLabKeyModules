(function (global, factory) {
  typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports, require('@jbrowse/core/Plugin'), require('@jbrowse/core/util'), require('react'), require('@jbrowse/core/configuration'), require('@jbrowse/core/util/types/mst'), require('@jbrowse/core/util/tracks'), require('@material-ui/core/utils'), require('@jbrowse/core/pluggableElementTypes/DisplayType')) :
  typeof define === 'function' && define.amd ? define(['exports', '@jbrowse/core/Plugin', '@jbrowse/core/util', 'react', '@jbrowse/core/configuration', '@jbrowse/core/util/types/mst', '@jbrowse/core/util/tracks', '@material-ui/core/utils', '@jbrowse/core/pluggableElementTypes/DisplayType'], factory) :
  (global = global || self, factory(global.JBrowsePluginMyProject = {}, global.JBrowseExports['@jbrowse/core/Plugin'], global.JBrowseExports['@jbrowse/core/util'], global.JBrowseExports.react, global.JBrowseExports['@jbrowse/core/configuration'], global.JBrowseExports['@jbrowse/core/util/types/mst'], global.JBrowseExports['@jbrowse/core/util/tracks'], global.JBrowseExports['@material-ui/core/utils'], global.JBrowseExports['@jbrowse/core/pluggableElementTypes/DisplayType']));
}(this, (function (exports, Plugin, util, React, configuration, mst, tracks, utils, DisplayType) { 'use strict';

  Plugin = Plugin && Object.prototype.hasOwnProperty.call(Plugin, 'default') ? Plugin['default'] : Plugin;
  var React__default = 'default' in React ? React['default'] : React;
  utils = utils && Object.prototype.hasOwnProperty.call(utils, 'default') ? utils['default'] : utils;
  DisplayType = DisplayType && Object.prototype.hasOwnProperty.call(DisplayType, 'default') ? DisplayType['default'] : DisplayType;

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

  function _defineProperty(obj, key, value) {
    if (key in obj) {
      Object.defineProperty(obj, key, {
        value: value,
        enumerable: true,
        configurable: true,
        writable: true
      });
    } else {
      obj[key] = value;
    }

    return obj;
  }

  function ownKeys(object, enumerableOnly) {
    var keys = Object.keys(object);

    if (Object.getOwnPropertySymbols) {
      var symbols = Object.getOwnPropertySymbols(object);
      if (enumerableOnly) symbols = symbols.filter(function (sym) {
        return Object.getOwnPropertyDescriptor(object, sym).enumerable;
      });
      keys.push.apply(keys, symbols);
    }

    return keys;
  }

  function _objectSpread2(target) {
    for (var i = 1; i < arguments.length; i++) {
      var source = arguments[i] != null ? arguments[i] : {};

      if (i % 2) {
        ownKeys(Object(source), true).forEach(function (key) {
          _defineProperty(target, key, source[key]);
        });
      } else if (Object.getOwnPropertyDescriptors) {
        Object.defineProperties(target, Object.getOwnPropertyDescriptors(source));
      } else {
        ownKeys(Object(source)).forEach(function (key) {
          Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key));
        });
      }
    }

    return target;
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
    var _useState = React.useState(''),
        _useState2 = _slicedToArray(_useState, 2),
        pushed = _useState2[0],
        setPushed = _useState2[1];

    return /*#__PURE__*/React__default.createElement("div", {
      style: {
        padding: 50
      }
    }, /*#__PURE__*/React__default.createElement("h1", null, "Hello plugin developers!"), /*#__PURE__*/React__default.createElement("button", {
      onClick: function onClick() {
        setPushed('Woah! You pushed the button! Great job!');
      }
    }, "Push the button"), /*#__PURE__*/React__default.createElement("p", null, pushed));
  }

  //import BaseFeatureDetailWidget from '@jbrowse/plugin-alignments'
  var HelloWidget = (function (jbrowse) {
    var _jbrowse$jbrequire = jbrowse.jbrequire('@material-ui/core'),
        Paper = _jbrowse$jbrequire.Paper,
        Table = _jbrowse$jbrequire.Table,
        TableCell = _jbrowse$jbrequire.TableCell,
        TableHead = _jbrowse$jbrequire.TableHead,
        TableRow = _jbrowse$jbrequire.TableRow;

    var _jbrowse$jbrequire2 = jbrowse.jbrequire('@material-ui/core/styles'),
        makeStyles = _jbrowse$jbrequire2.makeStyles;

    var _jbrowse$jbrequire3 = jbrowse.jbrequire('mobx-react'),
        observer = _jbrowse$jbrequire3.observer,
        MobxPropTypes = _jbrowse$jbrequire3.PropTypes;

    var PropTypes = jbrowse.jbrequire('prop-types');
    var React = jbrowse.jbrequire('react');

    var _jbrowse$jbrequire4 = jbrowse.jbrequire('@jbrowse/core/BaseFeatureWidget/BaseFeatureDetail'),
        BaseCard = _jbrowse$jbrequire4.BaseCard;

    var useStyles = makeStyles(function () {
      return {
        table: {
          padding: 0
        },
        link: {
          color: 'rgb(0, 0, 238)'
        }
      };
    });

    function NewTable(props) {
      var classes = useStyles();
      var model = props.model;
      var feat = JSON.parse(JSON.stringify(model.featureData));
      console.log(feat);
      return /*#__PURE__*/React.createElement(Paper, {
        className: classes.root,
        "data-testid": "hello-widget"
      }, /*#__PURE__*/React.createElement(BaseCard, {
        title: "NewTable"
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: '100%',
          maxHeight: 600,
          overflow: 'auto'
        }
      }, /*#__PURE__*/React.createElement(Table, {
        className: classes.table
      }, /*#__PURE__*/React.createElement(TableHead, null, /*#__PURE__*/React.createElement(TableRow, null, /*#__PURE__*/React.createElement(TableCell, null, feat.refName)), /*#__PURE__*/React.createElement(TableRow, null, /*#__PURE__*/React.createElement(TableCell, null, "Hello")))))));
    } //            <BaseFeatureDetails feature={feat} {...props} />
    //  <Divider />
    // volvox features
    // ALT
    // CHROM
    // FILTER
    // ID
    // INFO
    //  - AC1
    //  - AF1
    //  - DP
    //  - DP4
    //  - FQ
    //  - MQ
    //  - VDB
    // POS
    // QUAL
    // REF
    // description
    // end
    // refName
    // samples
    // start
    // type
    // uniqueId


    NewTable.propTypes = {
      model: MobxPropTypes.observableObject.isRequired
    };
    return observer(NewTable);
  });

  var HelloWidget$1 = (function (jbrowse) {
    var _jbrowse$jbrequire = jbrowse.jbrequire('mobx-state-tree'),
        types = _jbrowse$jbrequire.types;

    var configSchema = configuration.ConfigurationSchema('HelloWidget', {});
    var stateModel = types.model('HelloWidget', {
      id: mst.ElementId,
      type: types.literal('HelloWidget'),
      featureData: types.frozen({})
    }).actions(function (self) {
      return {
        setFeatureData: function setFeatureData(data) {
          self.featureData = data;
        },
        clearFeatureData: function clearFeatureData() {
          self.featureData = {};
        }
      };
    });
    var ReactComponent = jbrowse.jbrequire(HelloWidget);
    return {
      configSchema: configSchema,
      stateModel: stateModel,
      ReactComponent: ReactComponent
    };
  });

  var configSchemaF = (function (pluginManager) {
    var baseLinearDisplayConfigSchema = pluginManager.getPlugin('LinearGenomeViewPlugin').exports.baseLinearDisplayConfigSchema;
    return configuration.ConfigurationSchema('WidgetDisplay', {
      renderer: pluginManager.pluggableConfigSchemaType('renderer')
    }, {
      baseConfiguration: baseLinearDisplayConfigSchema,
      explicitlyTyped: true
    });
  });

  function unwrapExports (x) {
  	return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, 'default') ? x['default'] : x;
  }

  function createCommonjsModule(fn, module) {
  	return module = { exports: {} }, fn(module, module.exports), module.exports;
  }

  var interopRequireDefault = createCommonjsModule(function (module) {
  function _interopRequireDefault(obj) {
    return obj && obj.__esModule ? obj : {
      "default": obj
    };
  }

  module.exports = _interopRequireDefault;
  module.exports["default"] = module.exports, module.exports.__esModule = true;
  });

  unwrapExports(interopRequireDefault);

  var _typeof_1 = createCommonjsModule(function (module) {
  function _typeof(obj) {
    "@babel/helpers - typeof";

    if (typeof Symbol === "function" && typeof Symbol.iterator === "symbol") {
      module.exports = _typeof = function _typeof(obj) {
        return typeof obj;
      };

      module.exports["default"] = module.exports, module.exports.__esModule = true;
    } else {
      module.exports = _typeof = function _typeof(obj) {
        return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
      };

      module.exports["default"] = module.exports, module.exports.__esModule = true;
    }

    return _typeof(obj);
  }

  module.exports = _typeof;
  module.exports["default"] = module.exports, module.exports.__esModule = true;
  });

  unwrapExports(_typeof_1);

  var interopRequireWildcard = createCommonjsModule(function (module) {
  var _typeof = _typeof_1["default"];

  function _getRequireWildcardCache() {
    if (typeof WeakMap !== "function") return null;
    var cache = new WeakMap();

    _getRequireWildcardCache = function _getRequireWildcardCache() {
      return cache;
    };

    return cache;
  }

  function _interopRequireWildcard(obj) {
    if (obj && obj.__esModule) {
      return obj;
    }

    if (obj === null || _typeof(obj) !== "object" && typeof obj !== "function") {
      return {
        "default": obj
      };
    }

    var cache = _getRequireWildcardCache();

    if (cache && cache.has(obj)) {
      return cache.get(obj);
    }

    var newObj = {};
    var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor;

    for (var key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null;

        if (desc && (desc.get || desc.set)) {
          Object.defineProperty(newObj, key, desc);
        } else {
          newObj[key] = obj[key];
        }
      }
    }

    newObj["default"] = obj;

    if (cache) {
      cache.set(obj, newObj);
    }

    return newObj;
  }

  module.exports = _interopRequireWildcard;
  module.exports["default"] = module.exports, module.exports.__esModule = true;
  });

  unwrapExports(interopRequireWildcard);

  var createSvgIcon = createCommonjsModule(function (module, exports) {

  Object.defineProperty(exports, "__esModule", {
    value: true
  });
  Object.defineProperty(exports, "default", {
    enumerable: true,
    get: function get() {
      return utils.createSvgIcon;
    }
  });
  });

  unwrapExports(createSvgIcon);

  var FilterList = createCommonjsModule(function (module, exports) {





  Object.defineProperty(exports, "__esModule", {
    value: true
  });
  exports.default = void 0;

  var React = interopRequireWildcard(React__default);

  var _createSvgIcon = interopRequireDefault(createSvgIcon);

  var _default = (0, _createSvgIcon.default)( /*#__PURE__*/React.createElement("path", {
    d: "M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"
  }), 'FilterList');

  exports.default = _default;
  });

  var FilterListIcon = unwrapExports(FilterList);

  var modelF = (function (jbrowse) {
    var _jbrowse$jbrequire = jbrowse.jbrequire('mobx-state-tree'),
        types = _jbrowse$jbrequire.types;

    var configSchema = jbrowse.jbrequire(configSchemaF);
    var BaseLinearDisplay = jbrowse.getPlugin('LinearGenomeViewPlugin').exports.BaseLinearDisplay;
    return types.compose('WidgetDisplay', BaseLinearDisplay, types.model({
      type: types.literal('WidgetDisplay'),
      configuration: configuration.ConfigurationReference(configSchema)
    })).actions(function (self) {
      return {
        /*openFilterConfig() {
          const session = getSession(self)
          const editor = session.addWidget('HelloWidget', {
            target: self.parentTrack.configuration,
          })
          session.showWidget(editor)
        },*/
        selectFeature: function selectFeature(feature) {
          var session = util.getSession(self);
          var featureWidget = session.addWidget('HelloWidget', 'hWidget', {
            featureData: feature.toJSON()
          });
          session.showWidget(featureWidget);
          session.setSelection(feature);
        }
      };
    }).views(function (self) {
      return {
        get renderProps() {
          return _objectSpread2(_objectSpread2(_objectSpread2({}, self.composedRenderProps), tracks.getParentRenderProps(self)), {}, {
            config: self.configuration.renderer
          });
        },

        get rendererTypeName() {
          return self.configuration.renderer.type;
        },

        get trackMenuItems() {
          return [{
            label: 'Filter',
            onClick: self.openFilterConfig,
            icon: FilterListIcon
          }];
        }

      };
    });
  });

  var WidgetDisplay = (function (pluginManager) {
    return {
      configSchema: pluginManager.jbrequire(configSchemaF),
      stateModel: pluginManager.jbrequire(modelF)
    };
  });

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
    /*
      install(pluginManager: any) {
        pluginManager.addAdapterType(
          () =>
            new AdapterType ({
              name: "LGVHelloAdapter",
              configSchema,
              AdapterClass,
            })
        )
      }*/


    _createClass(MyProjectPlugin, [{
      key: "install",
      value: function install(pluginManager) {
        console.log("Installing plugins");
        var jbrequire = pluginManager.jbrequire;
        var types = pluginManager.lib['mobx-state-tree'].types;
        var ViewType = jbrequire('@jbrowse/core/pluggableElementTypes/ViewType');
        var WidgetType = jbrequire('@jbrowse/core/pluggableElementTypes/WidgetType');
        var LGVPlugin = pluginManager.getPlugin('LinearGenomeViewPlugin');
        var BaseLinearDisplayComponent = LGVPlugin.exports.BaseLinearDisplayComponent;
        var stateModel = types.model({
          type: types.literal('HelloView')
        }).actions(function () {
          return {
            setWidth: function setWidth() {// unused but required by your view
            }
          };
        });
        pluginManager.addDisplayType(function () {
          var _pluginManager$load = pluginManager.load(WidgetDisplay),
              configSchema = _pluginManager$load.configSchema,
              stateModel = _pluginManager$load.stateModel;

          return new DisplayType({
            name: 'WidgetDisplay',
            configSchema: configSchema,
            stateModel: stateModel,
            trackType: 'VariantTrack',
            viewType: 'LinearGenomeView',
            ReactComponent: BaseLinearDisplayComponent
          });
        });
        pluginManager.addWidgetType(function () {
          var _pluginManager$load2 = pluginManager.load(HelloWidget$1),
              configSchema = _pluginManager$load2.configSchema,
              ReactComponent = _pluginManager$load2.ReactComponent,
              stateModel = _pluginManager$load2.stateModel;

          return new WidgetType({
            name: 'HelloWidget',
            heading: 'Feature details',
            configSchema: configSchema,
            stateModel: stateModel,
            ReactComponent: ReactComponent
          });
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
        if (util.isAbstractMenuManager(pluginManager.rootModel)) {
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

  exports.default = MyProjectPlugin;

  Object.defineProperty(exports, '__esModule', { value: true });

})));
//# sourceMappingURL=jbrowse-plugin-my-project.umd.development.js.map
