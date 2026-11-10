import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async loader and tracks loading / error / data.
 *
 * Results from a superseded request are discarded, so rapid filter changes
 * cannot leave a stale response on screen.
 */
export function useApi(loader, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const [error, setError] = useState(null);
  const requestId = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await loader();
      if (mounted.current && id === requestId.current) {
        setData(result);
      }
      return result;
    } catch (err) {
      if (mounted.current && id === requestId.current) {
        setError(err);
        setData(null);
      }
      return undefined;
    } finally {
      if (mounted.current && id === requestId.current) {
        setLoading(false);
      }
    }
    // loader is intentionally re-created by callers via deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (immediate) {
      run();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run, immediate]);

  return { data, loading, error, reload: run, setData };
}

export default useApi;
