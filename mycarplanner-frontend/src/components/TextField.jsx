import { useId } from "react";

/** Floating-label input. The placeholder is a single space so :placeholder-shown can drive the label. */
export default function TextField({ label, hint, error, id: idProp, className, ...inputProps }) {
    const autoId = useId();
    const id = idProp ?? autoId;
    const hintId = hint ? `${id}-hint` : undefined;
    const errorId = error ? `${id}-error` : undefined;
    const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

    return (
        <div className={["text-field", className].filter(Boolean).join(" ")}>
            <input
                id={id}
                className="text-field-input"
                placeholder=" "
                aria-describedby={describedBy}
                aria-invalid={error ? true : undefined}
                {...inputProps}
            />
            <label htmlFor={id} className="text-field-label">
                {label}
            </label>
            {hint && (
                <p id={hintId} className="field-hint">
                    {hint}
                </p>
            )}
            {error && (
                <p id={errorId} className="field-error">
                    {error}
                </p>
            )}
        </div>
    );
}
