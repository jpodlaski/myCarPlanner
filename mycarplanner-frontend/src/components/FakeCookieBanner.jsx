import { useEffect, useState } from "react";
import "./FakeCookieBanner.css";

const STORAGE_KEY = "mycarplanner-cookie-easter-egg";

/**
 * Joke cookie banner: no cookies are set. Accept shows Psyduck for two seconds.
 */
export default function FakeCookieBanner() {
  const [visible, setVisible] = useState(() => {
    try {
      return sessionStorage.getItem(STORAGE_KEY) !== "1";
    } catch {
      return true;
    }
  });
  const [showPsyduck, setShowPsyduck] = useState(false);

  useEffect(() => {
    if (!showPsyduck) return undefined;
    const id = window.setTimeout(() => setShowPsyduck(false), 2000);
    return () => window.clearTimeout(id);
  }, [showPsyduck]);

  function dismiss() {
    try {
      sessionStorage.setItem(STORAGE_KEY, "1");
    } catch {
      // Storage blocked: the banner just reappears next visit.
    }
    setVisible(false);
  }

  function accept() {
    dismiss();
    setShowPsyduck(true);
  }

  if (!visible && !showPsyduck) return null;

  return (
    <>
      {visible && (
        <section className="fake-cookie-banner" aria-label="Cookie notice">
          <p className="fake-cookie-copy">
            We use cookies to improve your experience on this site. You can accept or decline
            non-essential cookies.
          </p>
          <div className="fake-cookie-actions">
            <button type="button" className="button button-neutral button-small" onClick={dismiss}>
              Decline
            </button>
            <button type="button" className="button button-primary button-small" onClick={accept}>
              Accept
            </button>
          </div>
        </section>
      )}
      {showPsyduck && (
        <div className="fake-cookie-psyduck" aria-hidden="true">
          <img src="/psyduck.png" alt="" />
        </div>
      )}
    </>
  );
}
