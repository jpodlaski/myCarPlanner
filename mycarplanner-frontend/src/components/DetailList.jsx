/** Label/value pairs as a description list. Items with `hidden: true` are skipped. */
export default function DetailList({ items }) {
    return (
        <dl className="detail-list">
            {items
                .filter((item) => !item.hidden)
                .map((item) => (
                    <div key={item.label} className="detail-item">
                        <dt>{item.label}</dt>
                        <dd>{item.value ?? "—"}</dd>
                    </div>
                ))}
        </dl>
    );
}
