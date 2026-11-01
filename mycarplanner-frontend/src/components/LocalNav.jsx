/**
 * Sticky product-style sub-navigation: title on the left, section switches and a
 * call to action on the right. Sections are in-page views, so the buttons carry
 * aria-current instead of being links.
 */
export default function LocalNav({ title, sections, activeSection, onSelect, action }) {
    const entries = sections ? Object.entries(sections) : [];

    return (
        <nav className="local-nav" aria-label={title}>
            <div className="local-nav-inner container">
                <p className="local-nav-title">{title}</p>
                <div className="local-nav-end">
                    {entries.length > 0 && (
                        <ul className="local-nav-list local-nav-sections">
                            {entries.map(([id, label]) => (
                                <li key={id}>
                                    <button
                                        type="button"
                                        className="local-nav-link"
                                        aria-current={activeSection === id ? "page" : undefined}
                                        onClick={() => onSelect(id)}
                                    >
                                        {label}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                    {action}
                </div>
            </div>
        </nav>
    );
}
