import { useEffect, useRef } from "react";

/**
 * Moves focus to the returned ref whenever `key` changes after the first render,
 * so screen-reader users hear the new section heading after switching views.
 */
export function useFocusOnChange(key) {
    const ref = useRef(null);
    const first = useRef(true);

    useEffect(() => {
        if (first.current) {
            first.current = false;
            return;
        }
        ref.current?.focus();
    }, [key]);

    return ref;
}
