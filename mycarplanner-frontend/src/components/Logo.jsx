/**
 * Brand marks cut from public/logo.png (transparent, so they sit on either theme).
 * "lockup" is wings + wordmark; "emblem" is the wings alone for the global nav.
 */
const VARIANTS = {
    lockup: { src: "/img/logo-lockup.png", width: 720, height: 383 },
    emblem: { src: "/img/logo-emblem.png", width: 210, height: 96 },
};

export default function Logo({ variant = "lockup", alt = "Hussarya", className }) {
    const { src, width, height } = VARIANTS[variant] ?? VARIANTS.lockup;
    return (
        <img
            className={className}
            src={src}
            width={width}
            height={height}
            alt={alt}
            decoding="async"
        />
    );
}
