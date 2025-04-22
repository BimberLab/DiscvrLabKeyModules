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
  IsIn             = 'is in',
  IsNotIn          = 'is not in',
  IsInAllOf        = 'is in all of',
  IsInAnyOf        = 'is in any of',
  IsNotInAnyOf     = 'is not in any of',
  IsNotInOneOf     = 'is not in one of',
  EqualsOneOf        = 'equals one of',
  None              = ''
}

export type Value = string | number

export interface Operator<T extends Value = Value> {
  key: OperatorKey
  label: string
  generateLucene(field: string, value: T): string
}

function fuzzyNumRange(field: string, raw: string | number) {
  const floatValue = parseFloat(String(raw))
  const intValue   = parseInt(String(raw), 10)

  if (floatValue === intValue) {
    return `[${intValue} TO ${intValue}]`
  }

  return `[${floatValue - 0.000001} TO ${floatValue + 0.000001}]`
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
 [OperatorKey.IsIn]: {
    key: OperatorKey.IsIn,
    label: 'Is In',
    generateLucene: (f, v: string) => `${f}:${v}`,
  },
  [OperatorKey.IsNotIn]: {
    key: OperatorKey.IsNotIn,
    label: 'Is Not In',
    generateLucene: (f, v: string) => `*:* -${f}:${v}`,
  },
  [OperatorKey.IsInAllOf]: {
    key: OperatorKey.IsInAllOf,
    label: 'Is In All Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `+${f}:${val}`).join(' '),
  },
  [OperatorKey.IsInAnyOf]: {
    key: OperatorKey.IsInAnyOf,
    label: 'Is In Any Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `${f}:${val}`).join(' OR '),
  },
  [OperatorKey.IsNotInAnyOf]: {
    key: OperatorKey.IsNotInAnyOf,
    label: 'Is Not In Any Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `*:* -${f}:${val}`).join(' AND '),
  },
  [OperatorKey.IsNotInOneOf]: {
    key: OperatorKey.IsNotInOneOf,
    label: 'Is Not In One Of',
    generateLucene: (f, v: string) =>
      v.split(',').map(val => `*:* -${f}:${val}`).join(' OR '),
  },
  [OperatorKey.None]: {
    key: OperatorKey.None,
    label: '',
    generateLucene: () => ''
  }
}