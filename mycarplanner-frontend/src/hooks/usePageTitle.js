import { useEffect } from "react";

const SITE_NAME = "myCarPlanner";

/** Sets document.title per route (WCAG 2.4.2). */
export function usePageTitle(title) {
    useEffect(() => {
        document.title = title ? `${title} – ${SITE_NAME}` : `${SITE_NAME} – Hussarya Service`;
    }, [title]);
}
