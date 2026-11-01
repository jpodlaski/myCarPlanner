import { useId } from "react";

/**
 * Disclosure list item. Only phrasing content goes inside the toggle button
 * (HTML forbids <div>/<p> there); details render in a sibling region.
 */
export default function ExpandableRow({
    title,
    eyebrow,
    badge,
    meta,
    trailing,
    expanded,
    onToggle,
    highlight = false,
    muted = false,
    children,
}) {
    const detailsId = useId();
    const className = [
        "row",
        expanded ? "row-open" : "",
        highlight ? "row-highlight" : "",
        muted ? "row-muted" : "",
    ].filter(Boolean).join(" ");

    return (
        <li className={className}>
            <div className="row-top">
                <button
                    type="button"
                    className="row-toggle"
                    aria-expanded={expanded}
                    aria-controls={expanded ? detailsId : undefined}
                    onClick={onToggle}
                >
                    <span className="row-text">
                        {eyebrow && <span className="row-eyebrow">{eyebrow}</span>}
                        <span className="row-title">{title}</span>
                        {meta && <span className="row-meta">{meta}</span>}
                    </span>
                    {badge && <span className="row-badge">{badge}</span>}
                    <span className="row-chevron" aria-hidden="true" />
                </button>
                {trailing}
            </div>
            {expanded && (
                <div id={detailsId} className="row-details">
                    {children}
                </div>
            )}
        </li>
    );
}
