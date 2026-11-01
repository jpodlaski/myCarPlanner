/** Previous / next pager. `page` is zero-based; renders nothing when everything fits on one page. */
export default function Pagination({ page, totalPages, totalItems, itemLabel = "items", label, onChange, disabled }) {
    if (totalPages <= 1) return null;

    return (
        <nav className="pagination" aria-label={label}>
            <button
                type="button"
                className="button button-neutral button-small"
                disabled={disabled || page === 0}
                onClick={() => onChange(page - 1)}
            >
                Previous
            </button>
            <p className="pagination-status" aria-live="polite">
                Page {page + 1} of {totalPages}
                <span className="pagination-total"> · {totalItems} {itemLabel}</span>
            </p>
            <button
                type="button"
                className="button button-neutral button-small"
                disabled={disabled || page >= totalPages - 1}
                onClick={() => onChange(page + 1)}
            >
                Next
            </button>
        </nav>
    );
}
