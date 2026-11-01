import { formatStatus } from "../utils/format";

export default function StatusBadge({ status }) {
    if (!status) return null;
    const slug = String(status).toLowerCase().replaceAll("_", "-");
    return <span className={`badge badge-${slug}`}>{formatStatus(status)}</span>;
}
