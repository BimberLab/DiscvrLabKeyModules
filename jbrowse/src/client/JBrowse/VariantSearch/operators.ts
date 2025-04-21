export enum OperatorKey {
  Equals             = 'equals',
  NotEquals          = 'does not equal',
  Contains           = 'contains',
  NotContains        = 'does not contain',
  StartsWith         = 'starts with',
  EndsWith           = 'ends with',
  IsEmpty            = 'is empty',
  IsNotEmpty         = 'is not empty',
  NumericEq          = '=',
  NumericNeq         = '!=',
  NumericGt          = '>',
  NumericGte         = '>=',
  NumericLt          = '<',
  NumericLte         = '<=',
  VariableIn         = 'variable in',
  NotVariableIn      = 'not variable in',
  VariableInAll      = 'variable in all of',
  VariableInAny      = 'variable in any of',
  NotVariableInAny  = 'not variable in any of',
  NotVariableInOne  = 'not variable in one of',
  EqualsOneOf        = 'equals one of',
  None              = ''
}

export type Value = string | number

export interface Operator<T extends Value = Value> {
  key: OperatorKey
  label: string
  generateLucene(field: string, value: T): string
}

function fuzzyNumRange(field: string, v: number) {
  return `[${v - 0.000001} TO ${v + 0.000001}]`
}

export const OperatorRegistry: Record<OperatorKey, Operator> = {
  [OperatorKey.Equals]: {
    key: OperatorKey.Equals,
    label: 'Equals',
    generateLucene: (f, v: string) => `${f}:${v}`,
  },
  [OperatorKey.NotEquals]: {
    key: OperatorKey.NotEquals,
    label: 'Does Not Equal',
    generateLucene: (f, v: string) => `*:* -${f}:${v}`,
  },
  [OperatorKey.Contains]: {
    key: OperatorKey.Contains,
    label: 'Contains',
    generateLucene: (f, v: string) => `${f}:*${v}*`,
  },
  [OperatorKey.NotContains]: {
    key: OperatorKey.NotContains,
    label: 'Does Not Contain',
    generateLucene: (f, v: string) => `*:* -${f}:*${v}*`,
  },
  [OperatorKey.StartsWith]: {
    key: OperatorKey.StartsWith,
    label: 'Starts With',
    generateLucene: (f, v: string) => `${f}:${v}*`,
  },
  [OperatorKey.EndsWith]: {
    key: OperatorKey.EndsWith,
    label: 'Ends With',
    generateLucene: (f, v: string) => `${f}:*${v}`,
  },
  [OperatorKey.IsEmpty]: {
    key: OperatorKey.IsEmpty,
    label: 'Is Empty',
    generateLucene: (f) => `*:* -${f}:*`,
  },
  [OperatorKey.IsNotEmpty]: {
    key: OperatorKey.IsNotEmpty,
    label: 'Is Not Empty',
    generateLucene: (f) => `${f}:*`,
  },
  [OperatorKey.NumericEq]: {
    key: OperatorKey.NumericEq,
    label: '=',
    generateLucene: (f, v: number) =>
      Number.isInteger(v) ? `${f}:[${v} TO ${v}]` : `${f}:${fuzzyNumRange(f, v)}`,
  },
  [OperatorKey.NumericNeq]: {
    key: OperatorKey.NumericNeq,
    label: '!=',
    generateLucene: (f, v: number) => {
      if (Number.isInteger(v)) {
        return `*:* -${f}:[${v} TO ${v}]`
      }
      const range = fuzzyNumRange(f, v)
      return `*:* -${f}:${range}`
    },
  },
  [OperatorKey.NumericGt]: {
    key: OperatorKey.NumericGt,
    label: '>',
    generateLucene: (f, v: number) =>
      Number.isInteger(v) ? `${f}:{${v} TO *]` : `${f}:[${v + 0.000001} TO *]`,
  },
  [OperatorKey.NumericGte]: {
    key: OperatorKey.NumericGte,
    label: '>=',
    generateLucene: (f, v: number) => `${f}:[${v} TO *]`,
  },
  [OperatorKey.NumericLt]: {
    key: OperatorKey.NumericLt,
    label: '<',
    generateLucene: (f, v: number) =>
      Number.isInteger(v) ? `${f}:[* TO ${v}}` : `${f}:[* TO ${v - 0.000001}]`,
  },
  [OperatorKey.NumericLte]: {
    key: OperatorKey.NumericLte,
    label: '<=',
    generateLucene: (f, v: number) => `${f}:[* TO ${v}]`,
  },
  [OperatorKey.EqualsOneOf]: {
    key: OperatorKey.EqualsOneOf,
    label: 'Equals One Of',
    generateLucene: (f, v: string) => `${f}:~${v}~`,
  },
  [OperatorKey.VariableIn]: {
    key: OperatorKey.VariableIn,
    label: 'Variable In',
    generateLucene: (f, v: string) => `${f}:${v}`,
  },
  [OperatorKey.NotVariableIn]: {
    key: OperatorKey.NotVariableIn,
    label: 'Not Variable In',
    generateLucene: (f, v: string) => `*:* -${f}:${v}`,
  },
  [OperatorKey.VariableInAll]: {
    key: OperatorKey.VariableInAll,
    label: 'Variable In All Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `+${f}:${val}`).join(' '),
  },
  [OperatorKey.VariableInAny]: {
    key: OperatorKey.VariableInAny,
    label: 'Variable In Any Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `${f}:${val}`).join(' OR '),
  },
  [OperatorKey.NotVariableInAny]: {
    key: OperatorKey.NotVariableInAny,
    label: 'Not Variable In Any Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `*:* -${f}:${val}`).join(' AND '),
  },
  [OperatorKey.NotVariableInOne]: {
    key: OperatorKey.NotVariableInOne,
    label: 'Not Variable In One Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `*:* -${f}:${val}`).join(' OR '),
  },
  [OperatorKey.None]: {
    key: OperatorKey.None,
    label: '',
    generateLucene: () => ''
  }
}