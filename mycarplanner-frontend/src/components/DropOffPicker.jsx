import { useId, useMemo, useState } from "react";
import "./DropOffPicker.css";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function pad2(n) {
    return String(n).padStart(2, "0");
}

function toDateKey(date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

function buildTimeSlots(startHour, endHour, stepMinutes) {
    const slots = [];
    for (let minutes = startHour * 60; minutes <= endHour * 60; minutes += stepMinutes) {
        const h = Math.floor(minutes / 60);
        const m = minutes % 60;
        slots.push(`${pad2(h)}:${pad2(m)}`);
    }
    return slots;
}

function startOfDay(date) {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    return d;
}

function monthLabel(year, monthIndex) {
    return new Date(year, monthIndex, 1).toLocaleDateString(undefined, {
        month: "long",
        year: "numeric",
    });
}

function fullDateLabel(date) {
    return date.toLocaleDateString(undefined, {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
    });
}

function buildMonthCells(year, monthIndex) {
    // Monday-first grid; cells are Date or null for padding.
    const first = new Date(year, monthIndex, 1);
    const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
    const mondayIndex = (first.getDay() + 6) % 7;
    const cells = [];

    for (let i = 0; i < mondayIndex; i += 1) {
        cells.push(null);
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
        cells.push(new Date(year, monthIndex, day));
    }
    while (cells.length % 7 !== 0) {
        cells.push(null);
    }
    return cells;
}

/**
 * Workshop drop-off picker: month calendar + time chips (06:00–20:00).
 * Past days, and earlier slots today, are disabled.
 */
export default function DropOffPicker({
    dateValue,
    timeValue,
    onDateChange,
    onTimeChange,
    startHour = 6,
    endHour = 20,
    stepMinutes = 30,
}) {
    const monthId = useId();
    const timeHeadingId = useId();
    const today = useMemo(() => startOfDay(new Date()), []);
    const timeSlots = useMemo(
        () => buildTimeSlots(startHour, endHour, stepMinutes),
        [startHour, endHour, stepMinutes]
    );

    const initial = dateValue ? new Date(`${dateValue}T12:00:00`) : today;
    const [viewYear, setViewYear] = useState(initial.getFullYear());
    const [viewMonth, setViewMonth] = useState(initial.getMonth());

    const cells = useMemo(
        () => buildMonthCells(viewYear, viewMonth),
        [viewYear, viewMonth]
    );

    const shiftMonth = (delta) => {
        const next = new Date(viewYear, viewMonth + delta, 1);
        setViewYear(next.getFullYear());
        setViewMonth(next.getMonth());
    };

    const canGoPrev = useMemo(() => {
        const prevMonthEnd = new Date(viewYear, viewMonth, 0);
        return prevMonthEnd >= today;
    }, [viewYear, viewMonth, today]);

    const selectedLabel = dateValue
        ? new Date(`${dateValue}T12:00:00`).toLocaleDateString(undefined, {
            weekday: "short",
            day: "numeric",
            month: "short",
            year: "numeric",
        })
        : null;

    const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();
    const isTodaySelected = dateValue === toDateKey(today);

    const isSlotDisabled = (slot) => {
        if (!dateValue) return true;
        if (!isTodaySelected) return false;
        const [h, m] = slot.split(":").map(Number);
        return h * 60 + m <= nowMinutes;
    };

    return (
        <div className="dropoff-picker">
            <div className="dropoff-calendar" role="group" aria-labelledby={monthId}>
                <div className="dropoff-cal-header">
                    <button
                        type="button"
                        className="dropoff-nav-btn"
                        onClick={() => shiftMonth(-1)}
                        disabled={!canGoPrev}
                        aria-label="Previous month"
                    >
                        <span className="dropoff-nav-icon dropoff-nav-prev" aria-hidden="true" />
                    </button>
                    <p id={monthId} className="dropoff-month-label" aria-live="polite">
                        {monthLabel(viewYear, viewMonth)}
                    </p>
                    <button
                        type="button"
                        className="dropoff-nav-btn"
                        onClick={() => shiftMonth(1)}
                        aria-label="Next month"
                    >
                        <span className="dropoff-nav-icon dropoff-nav-next" aria-hidden="true" />
                    </button>
                </div>

                <div className="dropoff-weekdays" aria-hidden="true">
                    {WEEKDAYS.map((day) => (
                        <span key={day}>{day}</span>
                    ))}
                </div>

                <div className="dropoff-grid">
                    {cells.map((date, index) => {
                        if (!date) {
                            return <span key={`empty-${index}`} className="dropoff-day empty" aria-hidden="true" />;
                        }
                        const key = toDateKey(date);
                        const disabled = startOfDay(date) < today;
                        const selected = key === dateValue;
                        const isToday = key === toDateKey(today);

                        return (
                            <button
                                key={key}
                                type="button"
                                className={[
                                    "dropoff-day",
                                    selected ? "selected" : "",
                                    isToday ? "today" : "",
                                ].filter(Boolean).join(" ")}
                                disabled={disabled}
                                aria-pressed={selected}
                                aria-current={isToday ? "date" : undefined}
                                aria-label={fullDateLabel(date)}
                                onClick={() => onDateChange(selected ? "" : key)}
                            >
                                {date.getDate()}
                            </button>
                        );
                    })}
                </div>
            </div>

            <div className="dropoff-time-panel" role="group" aria-labelledby={timeHeadingId}>
                <p id={timeHeadingId} className="dropoff-time-heading">
                    {selectedLabel ? `Time on ${selectedLabel}` : "Time"}
                </p>
                <p className="dropoff-time-hours">
                    {selectedLabel
                        ? `Workshop hours ${pad2(startHour)}:00–${pad2(endHour)}:00`
                        : "Pick a date first."}
                </p>
                <div className="dropoff-time-grid">
                    {timeSlots.map((slot) => {
                        const selected = slot === timeValue;
                        const disabled = isSlotDisabled(slot);
                        return (
                            <button
                                key={slot}
                                type="button"
                                aria-pressed={selected}
                                className={`dropoff-time-slot${selected ? " selected" : ""}`}
                                disabled={disabled}
                                onClick={() => onTimeChange(selected ? "" : slot)}
                            >
                                {slot}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
