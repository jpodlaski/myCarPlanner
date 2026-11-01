import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import DetailList from "../../components/DetailList";
import ExpandableRow from "../../components/ExpandableRow";
import PasswordCard from "../../components/PasswordCard";
import StatusBadge from "../../components/StatusBadge";
import { useFocusOnChange } from "../../hooks/useFocusOnChange";
import { api } from "../../services/api";
import { clearSession, getSession } from "../../services/auth";
import { formatCustomerDateTime } from "../../utils/dateTime";
import { formatEuro, formatStatus } from "../../utils/format";

const SECTIONS = {
    vehicles: "Vehicles",
    bookings: "Bookings",
    archive: "Archive",
    account: "Account",
};

const LEDES = {
    vehicles: "The cars on your account. Remove one any time; its service history stays.",
    bookings: "Open service requests, from scheduled to in progress.",
    archive: "Completed and cancelled bookings, with invoices.",
    account: "Sign-in and account settings.",
};

function isWorkshopCancellation(booking) {
    if (booking?.cancelledBy === "WORKER" || booking?.cancelledBy === "WORKSHOP") {
        return true;
    }
    // Rows from before cancelledBy existed only carry a reason; treat those as workshop-initiated.
    return Boolean(booking?.cancellationReason?.trim()) && booking?.cancelledBy !== "CUSTOMER";
}

function cancellationSourceLabel(cancelledBy) {
    if (cancelledBy === "CUSTOMER") return "You";
    if (cancelledBy === "WORKER" || cancelledBy === "WORKSHOP") return "Workshop";
    return null;
}

function getBookingVehicleId(booking) {
    return booking.vehicle?.id ?? booking.vehicleId;
}

function formatVehicleOption(vehicle) {
    return [
        vehicle.modelLine ?? vehicle.model,
        vehicle.productionYear ? String(vehicle.productionYear) : null,
        vehicle.vin,
    ].filter(Boolean).join(" · ");
}

function vehicleDetailItems(vehicle) {
    return [
        { label: "Model", value: vehicle.modelLine ?? vehicle.model },
        { label: "Production year", value: vehicle.productionYear },
        { label: "Body", value: vehicle.bodyStyle },
        { label: "VIN", value: vehicle.vin },
        {
            label: "Engine",
            value: vehicle.engine
                ? `${vehicle.engine}${vehicle.power ? ` (${vehicle.power})` : ""}`
                : null,
        },
        {
            label: "Transmission",
            value: vehicle.transmission
                ? `${vehicle.transmission}${vehicle.drivetrain ? ` · ${vehicle.drivetrain}` : ""}`
                : null,
        },
    ];
}

function getBookingVin(booking, vehicles = []) {
    if (booking.vehicle?.vin) return booking.vehicle.vin;
    if (booking.vin) return booking.vin;
    const vehicleId = getBookingVehicleId(booking);
    if (vehicleId == null) return null;
    return vehicles.find((vehicle) => vehicle.id === vehicleId)?.vin ?? null;
}

// Open bookings block vehicle removal on both client and server.
function isOpenBooking(status) {
    return status === "SCHEDULED"
        || status === "READY_FOR_WORK"
        || status === "IN_PROGRESS";
}

function isArchiveStatus(status) {
    return status === "COMPLETED" || status === "CANCELLED";
}

function clientArchiveMatchesQuery(booking, query, vehicles) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const vin = getBookingVin(booking, vehicles);
    const cancelledBy = cancellationSourceLabel(booking.cancelledBy);
    const haystack = [
        booking.carModel,
        vin,
        booking.status,
        formatStatus(booking.status),
        booking.cancellationReason,
        cancelledBy,
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
    return haystack.includes(q);
}

/** Client home: the local nav switches between Vehicles, Bookings, Archive and Account. */
export default function ClientDashboard() {
    const navigate = useNavigate();
    const session = getSession();

    const [section, setSection] = useState("vehicles");
    const headingRef = useFocusOnChange(section);
    const [showPasswordForm, setShowPasswordForm] = useState(false);
    const [showDeleteAccount, setShowDeleteAccount] = useState(false);
    const [deleteAccountMessage, setDeleteAccountMessage] = useState("");
    const [deleteAccountError, setDeleteAccountError] = useState("");
    const [deleteAccountLoading, setDeleteAccountLoading] = useState(false);

    const [vehicles, setVehicles] = useState([]);
    const [vehicleHistory, setVehicleHistory] = useState([]);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [bookingError, setBookingError] = useState("");
    const [vehicleError, setVehicleError] = useState("");
    const [removingVehicleId, setRemovingVehicleId] = useState(null);
    const [cancellingBookingId, setCancellingBookingId] = useState(null);
    const [downloadingInvoiceId, setDownloadingInvoiceId] = useState(null);
    const [bookingVehicleFilter, setBookingVehicleFilter] = useState("");
    const [archiveQuery, setArchiveQuery] = useState("");
    const [showVehicleHistory, setShowVehicleHistory] = useState(false);
    const [expandedBookingId, setExpandedBookingId] = useState(null);
    const [expandedVehicleId, setExpandedVehicleId] = useState(null);
    const [cancelForms, setCancelForms] = useState({});

    const loadData = useCallback(async () => {
        if (!session?.token) return;
        setLoading(true);
        setError("");
        try {
            const [vehiclesRes, historyRes, bookingsRes] = await Promise.all([
                api.get("/vehicles/me"),
                api.get("/vehicles/me/history"),
                api.get("/customers/me/bookings"),
            ]);
            setVehicles(vehiclesRes.data);
            setVehicleHistory(historyRes.data);
            setBookings(
                [...bookingsRes.data].sort((a, b) => Number(b.id) - Number(a.id))
            );
        } catch {
            setError("Could not load your account data.");
        } finally {
            setLoading(false);
        }
    }, [session?.token]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const vehicleIdsWithOpenBookings = useMemo(() => {
        const ids = new Set();
        for (const booking of bookings) {
            if (!isOpenBooking(booking.status)) continue;
            const vehicleId = getBookingVehicleId(booking);
            if (vehicleId != null) ids.add(vehicleId);
        }
        return ids;
    }, [bookings]);

    const filteredBookings = useMemo(() => {
        if (!bookingVehicleFilter) return bookings;
        const selectedId = Number(bookingVehicleFilter);
        return bookings.filter((booking) => getBookingVehicleId(booking) === selectedId);
    }, [bookings, bookingVehicleFilter]);

    const activeBookings = useMemo(
        () => filteredBookings.filter((booking) => isOpenBooking(booking.status)),
        [filteredBookings]
    );

    const allKnownVehicles = useMemo(
        () => [...vehicles, ...vehicleHistory],
        [vehicles, vehicleHistory]
    );

    const hasArchive = bookings.some((b) => isArchiveStatus(b.status));

    const archiveBookings = useMemo(
        () => bookings
            .filter((booking) => isArchiveStatus(booking.status))
            .filter((booking) => clientArchiveMatchesQuery(booking, archiveQuery, allKnownVehicles)),
        [bookings, archiveQuery, allKnownVehicles]
    );

    useEffect(() => {
        if (!bookingVehicleFilter) return;
        const selectedId = Number(bookingVehicleFilter);
        if (!vehicles.some((vehicle) => vehicle.id === selectedId)) {
            setBookingVehicleFilter("");
        }
    }, [vehicles, bookingVehicleFilter]);

    const logout = () => {
        clearSession();
        navigate("/");
    };

    const selectSection = (next) => {
        setSection(next);
        if (next !== "archive") setArchiveQuery("");
        if (next !== "account") {
            setShowPasswordForm(false);
            setShowDeleteAccount(false);
            setDeleteAccountMessage("");
            setDeleteAccountError("");
        }
    };

    const requestAccountDeletion = async () => {
        setDeleteAccountError("");
        setDeleteAccountMessage("");
        setDeleteAccountLoading(true);
        try {
            const res = await api.post(
                "/auth/request-account-deletion",
                {},
                { skipAuthRedirect: true }
            );
            setDeleteAccountMessage(
                res.data?.message
                ?? "Check your email to confirm account deletion."
            );
        } catch (err) {
            setDeleteAccountError(
                err.response?.data?.message ?? "Could not start account deletion."
            );
        } finally {
            setDeleteAccountLoading(false);
        }
    };

    const removeVehicle = async (vehicle) => {
        if (vehicleIdsWithOpenBookings.has(vehicle.id)) {
            setVehicleError(
                "Cannot remove a vehicle while it has a pending or in-progress booking. " +
                "Cancel or complete the booking first."
            );
            return;
        }

        const confirmed = window.confirm(
            `Remove ${vehicle.modelLine ?? vehicle.model} (${vehicle.vin}) from your account? ` +
            "Your past service records are kept."
        );
        if (!confirmed) return;

        setVehicleError("");
        setRemovingVehicleId(vehicle.id);

        try {
            await api.delete(`/vehicles/${vehicle.id}`);
            loadData();
        } catch (err) {
            setVehicleError(err.response?.data?.message ?? "Could not remove vehicle.");
        } finally {
            setRemovingVehicleId(null);
        }
    };

    const cancelBooking = async (booking) => {
        const reason = cancelForms[booking.id]?.reason?.trim();
        if (!reason) {
            setExpandedBookingId(booking.id);
            setBookingError("A reason is required to cancel a booking.");
            return;
        }

        setBookingError("");
        setCancellingBookingId(booking.id);

        try {
            await api.post(`/bookings/${booking.id}/cancel`, { reason });
            setCancelForms((prev) => {
                const next = { ...prev };
                delete next[booking.id];
                return next;
            });
            loadData();
        } catch (err) {
            setBookingError(err.response?.data?.message ?? "Could not cancel booking.");
        } finally {
            setCancellingBookingId(null);
        }
    };

    const downloadInvoice = async (booking) => {
        setBookingError("");
        setDownloadingInvoiceId(booking.id);
        try {
            const res = await api.get(`/bookings/${booking.id}/invoice`, {
                responseType: "blob",
            });
            const url = window.URL.createObjectURL(res.data);
            const link = document.createElement("a");
            link.href = url;
            link.download = `invoice-booking-${booking.id}.pdf`;
            link.click();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            let message = "Could not download invoice.";
            const data = err.response?.data;
            if (data instanceof Blob) {
                try {
                    const text = await data.text();
                    const parsed = JSON.parse(text);
                    if (parsed?.message) message = parsed.message;
                } catch {
                    // Not JSON: keep the generic message.
                }
            } else if (err.response?.data?.message) {
                message = err.response.data.message;
            }
            setBookingError(message);
        } finally {
            setDownloadingInvoiceId(null);
        }
    };

    const renderBookingRow = (booking, { archive = false } = {}) => {
        const vin = getBookingVin(booking, allKnownVehicles);
        const expandedKey = archive ? `archive-${booking.id}` : booking.id;
        const expanded = expandedBookingId === expandedKey;
        const canCancel = isOpenBooking(booking.status);
        const cancelled = booking.status === "CANCELLED";

        return (
            <ExpandableRow
                key={booking.id}
                eyebrow={`Booking #${booking.id}`}
                title={booking.carModel}
                meta={vin ? `VIN ${vin}` : "VIN —"}
                badge={<StatusBadge status={booking.status} />}
                highlight={!archive && booking.status === "IN_PROGRESS"}
                expanded={expanded}
                onToggle={() =>
                    setExpandedBookingId((current) =>
                        current === expandedKey ? null : expandedKey
                    )
                }
            >
                <DetailList
                    items={[
                        { label: "Status", value: formatStatus(booking.status) },
                        {
                            label: "Estimated drop-off",
                            value: booking.estimatedDropOffTime
                                ? formatCustomerDateTime(booking.estimatedDropOffTime)
                                : null,
                        },
                        {
                            label: "Appointment",
                            value: booking.scheduledDateTime
                                ? formatCustomerDateTime(booking.scheduledDateTime)
                                : null,
                        },
                        { label: "Issue", value: booking.customerDescription || null },
                        {
                            label: "Work planned",
                            value: booking.serviceTypes?.length
                                ? booking.serviceTypes.join(", ")
                                : null,
                        },
                        { label: "Estimated cost", value: formatEuro(booking.estimatedCost) },
                        { label: "Final cost", value: formatEuro(booking.finalCost) },
                        {
                            label: "Availability notes",
                            value: booking.availabilityNotes,
                            hidden: !booking.availabilityNotes,
                        },
                        {
                            label: "Cancelled by",
                            value: cancellationSourceLabel(booking.cancelledBy)
                                ?? (isWorkshopCancellation(booking) ? "Workshop" : null),
                            hidden: !cancelled,
                        },
                        {
                            label: "Cancellation reason",
                            value: booking.cancellationReason?.trim() || null,
                            hidden: !cancelled,
                        },
                    ]}
                />

                {booking.status === "COMPLETED" && (
                    <div className="button-row">
                        <button
                            type="button"
                            className="button button-secondary button-small"
                            disabled={downloadingInvoiceId === booking.id}
                            onClick={() => downloadInvoice(booking)}
                        >
                            {downloadingInvoiceId === booking.id
                                ? "Downloading…"
                                : "Download invoice"}
                        </button>
                    </div>
                )}

                {canCancel && (
                    <form
                        className="row-section inline-action"
                        onSubmit={(e) => {
                            e.preventDefault();
                            cancelBooking(booking);
                        }}
                    >
                        <label className="field">
                            <span className="field-label">Cancellation reason</span>
                            <input
                                className="input"
                                aria-describedby={`cancel-hint-${booking.id}`}
                                value={cancelForms[booking.id]?.reason ?? ""}
                                onChange={(e) =>
                                    setCancelForms((prev) => ({
                                        ...prev,
                                        [booking.id]: {
                                            ...prev[booking.id],
                                            reason: e.target.value,
                                        },
                                    }))
                                }
                            />
                            <span id={`cancel-hint-${booking.id}`} className="field-hint">
                                Required to cancel.
                            </span>
                        </label>
                        <button
                            type="submit"
                            className="button button-danger"
                            disabled={cancellingBookingId === booking.id}
                        >
                            {cancellingBookingId === booking.id
                                ? "Cancelling…"
                                : "Cancel booking"}
                        </button>
                    </form>
                )}
            </ExpandableRow>
        );
    };

    const renderVehicleRow = (vehicle, { former = false } = {}) => {
        const key = former ? `history-${vehicle.id}` : vehicle.id;
        const expanded = expandedVehicleId === key;
        const hasOpenBooking = !former && vehicleIdsWithOpenBookings.has(vehicle.id);

        return (
            <ExpandableRow
                key={vehicle.id}
                eyebrow={vehicle.productionYear ? String(vehicle.productionYear) : undefined}
                title={vehicle.modelLine ?? vehicle.model}
                meta={`VIN ${vehicle.vin}`}
                badge={
                    former
                        ? <span className="badge">Former</span>
                        : hasOpenBooking
                            ? <span className="badge badge-in-progress">In service</span>
                            : null
                }
                muted={former}
                expanded={expanded}
                onToggle={() =>
                    setExpandedVehicleId((current) => (current === key ? null : key))
                }
            >
                <DetailList items={vehicleDetailItems(vehicle)} />
                {!former && (
                    <div className="row-section stack">
                        {hasOpenBooking && (
                            <p className="card-text">
                                Cannot remove while a booking is pending or in progress.
                            </p>
                        )}
                        <div className="button-row">
                            <button
                                type="button"
                                className="button button-danger button-small"
                                disabled={removingVehicleId === vehicle.id || hasOpenBooking}
                                onClick={() => removeVehicle(vehicle)}
                            >
                                {removingVehicleId === vehicle.id ? "Removing…" : "Remove vehicle"}
                            </button>
                        </div>
                    </div>
                )}
            </ExpandableRow>
        );
    };

    const loadingStatus = loading && (
        <p className="status-text" role="status">
            Loading…
        </p>
    );
    const loadError = error && (
        <p className="message message-error" role="alert">
            {error}
        </p>
    );

    return (
        <AppShell
            pageTitle={SECTIONS[section]}
            title="Garage"
            homeTo="/client"
            userName={session?.name}
            onSignOut={logout}
            sections={SECTIONS}
            activeSection={section}
            onSelectSection={selectSection}
            localAction={
                vehicles.length > 0 ? (
                    <button
                        type="button"
                        className="button button-primary button-small"
                        onClick={() => navigate("/client/request-service")}
                    >
                        Request service
                    </button>
                ) : null
            }
        >
            <header className="page-header">
                <h1 ref={headingRef} tabIndex={-1} className="page-title">
                    {SECTIONS[section]}
                </h1>
                <p className="page-lede">{LEDES[section]}</p>
            </header>

            {section === "vehicles" && (
                <div className="stack">
                    <div className="button-row">
                        <button
                            type="button"
                            className="button button-secondary"
                            onClick={() => navigate("/client/add-vehicle")}
                        >
                            Add a vehicle
                        </button>
                    </div>
                    {loadingStatus}
                    {loadError}
                    {vehicleError && (
                        <p className="message message-error" role="alert">
                            {vehicleError}
                        </p>
                    )}
                    {!loading && !error && vehicles.length === 0 && (
                        <p className="empty-state">No vehicles yet. Add your first Hussarya to book service.</p>
                    )}
                    {vehicles.length > 0 && (
                        <ul className="row-list">
                            {vehicles.map((vehicle) => renderVehicleRow(vehicle))}
                        </ul>
                    )}

                    {!loading && vehicleHistory.length > 0 && (
                        <div className="former-block">
                            <button
                                type="button"
                                className="disclosure-btn"
                                aria-expanded={showVehicleHistory}
                                aria-controls="former-vehicles"
                                onClick={() => setShowVehicleHistory((open) => !open)}
                            >
                                Former vehicles ({vehicleHistory.length})
                                <span className="row-chevron" aria-hidden="true" />
                            </button>
                            {showVehicleHistory && (
                                <ul id="former-vehicles" className="row-list">
                                    {vehicleHistory.map((vehicle) =>
                                        renderVehicleRow(vehicle, { former: true })
                                    )}
                                </ul>
                            )}
                        </div>
                    )}
                </div>
            )}

            {section === "bookings" && (
                <div className="stack">
                    {loadingStatus}
                    {loadError}
                    {bookingError && (
                        <p className="message message-error" role="alert">
                            {bookingError}
                        </p>
                    )}
                    {!loading && vehicles.length === 0 && (
                        <div className="empty-state stack">
                            <p>Add a vehicle before requesting service.</p>
                            <div>
                                <button
                                    type="button"
                                    className="button button-primary"
                                    onClick={() => navigate("/client/add-vehicle")}
                                >
                                    Add a vehicle
                                </button>
                            </div>
                        </div>
                    )}
                    {!loading && (activeBookings.length > 0 || vehicles.length > 0) && (
                        <div className="toolbar">
                            <label className="field">
                                <span className="field-label">Vehicle</span>
                                <select
                                    className="select"
                                    value={bookingVehicleFilter}
                                    onChange={(e) => setBookingVehicleFilter(e.target.value)}
                                >
                                    <option value="">All vehicles</option>
                                    {vehicles.map((vehicle) => (
                                        <option key={vehicle.id} value={vehicle.id}>
                                            {formatVehicleOption(vehicle)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        </div>
                    )}
                    {!loading && vehicles.length > 0 && activeBookings.length === 0 && (
                        <p className="empty-state">No open bookings.</p>
                    )}
                    {activeBookings.length > 0 && (
                        <ul className="row-list">
                            {activeBookings.map((booking) => renderBookingRow(booking))}
                        </ul>
                    )}
                </div>
            )}

            {section === "archive" && (
                <div className="stack">
                    {loadingStatus}
                    {loadError}
                    {bookingError && (
                        <p className="message message-error" role="alert">
                            {bookingError}
                        </p>
                    )}
                    {!loading && hasArchive && (
                        <div className="toolbar">
                            <label className="field">
                                <span className="field-label">Search archive</span>
                                <input
                                    className="input"
                                    type="search"
                                    placeholder="Model, VIN, status, reason"
                                    value={archiveQuery}
                                    onChange={(e) => setArchiveQuery(e.target.value)}
                                />
                            </label>
                        </div>
                    )}
                    {!loading && !hasArchive && (
                        <p className="empty-state">No completed or cancelled bookings yet.</p>
                    )}
                    {hasArchive && archiveBookings.length === 0 && (
                        <p className="empty-state" role="status">
                            No bookings match “{archiveQuery}”.
                        </p>
                    )}
                    {archiveBookings.length > 0 && (
                        <ul className="row-list">
                            {archiveBookings.map((booking) =>
                                renderBookingRow(booking, { archive: true })
                            )}
                        </ul>
                    )}
                </div>
            )}

            {section === "account" && (
                <div className="stack">
                    <PasswordCard
                        open={showPasswordForm}
                        onOpenChange={(open) => {
                            if (open) setShowDeleteAccount(false);
                            setShowPasswordForm(open);
                        }}
                    />

                    <section className="card card-danger" aria-labelledby="delete-account-title">
                        <div className="card-header">
                            <h2 id="delete-account-title" className="card-title">
                                Delete account
                            </h2>
                        </div>
                        <div className="stack">
                            <p className="card-text">
                                Open service bookings must be cancelled or completed first.
                                We will email you a confirmation link before anything is deleted.
                            </p>

                            {!showDeleteAccount ? (
                                <div className="button-row">
                                    <button
                                        type="button"
                                        className="button button-danger button-small"
                                        onClick={() => {
                                            setShowPasswordForm(false);
                                            setDeleteAccountMessage("");
                                            setDeleteAccountError("");
                                            setShowDeleteAccount(true);
                                        }}
                                    >
                                        Delete account
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <p className="message message-info">
                                        We will send a confirmation link to your email.
                                        Your account stays active until you open that link.
                                    </p>
                                    <div className="button-row">
                                        <button
                                            type="button"
                                            className="button button-danger button-small"
                                            disabled={deleteAccountLoading || Boolean(deleteAccountMessage)}
                                            onClick={requestAccountDeletion}
                                        >
                                            {deleteAccountLoading
                                                ? "Sending…"
                                                : "Send confirmation email"}
                                        </button>
                                        <button
                                            type="button"
                                            className="button button-neutral button-small"
                                            disabled={deleteAccountLoading}
                                            onClick={() => {
                                                setShowDeleteAccount(false);
                                                setDeleteAccountMessage("");
                                                setDeleteAccountError("");
                                            }}
                                        >
                                            Cancel
                                        </button>
                                    </div>
                                    {deleteAccountError && (
                                        <p className="message message-error" role="alert">
                                            {deleteAccountError}
                                        </p>
                                    )}
                                    {deleteAccountMessage && (
                                        <p className="message message-success" role="status">
                                            {deleteAccountMessage}
                                        </p>
                                    )}
                                </>
                            )}
                        </div>
                    </section>
                </div>
            )}
        </AppShell>
    );
}
