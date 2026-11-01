/** "READY_FOR_WORK" -> "Ready for work" */
export function formatStatus(status) {
    if (!status) return "";
    const text = String(status).replaceAll("_", " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formatEuro(value) {
    return value != null ? `${value} EUR` : null;
}
