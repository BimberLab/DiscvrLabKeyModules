// This file is a modification to the useRef hook necessary for filterRenderers to work properly. Ripped from the
// ReactDataGrid documentation/demo here: https://github.com/adazzle/react-data-grid/blob/main/src/hooks/useFocusRef.ts

import { useRef } from 'react'

// eslint-disable-next-line @typescript-eslint/no-restricted-imports
import { useEffect, useLayoutEffect as useOriginalLayoutEffect } from 'react'
const useLayoutEffect = typeof window === 'undefined' ? useEffect : useOriginalLayoutEffect


export default function useFocusRef<T extends HTMLOrSVGElement>(isSelected: boolean) {
  const ref = useRef<T>(null);

  useLayoutEffect(() => {
    if (!isSelected) return;
    ref.current?.focus({ preventScroll: true });
  }, [isSelected]);

  return {
    ref,
    tabIndex: isSelected ? 0 : -1
  };
}