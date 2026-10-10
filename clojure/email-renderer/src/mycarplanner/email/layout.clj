(ns mycarplanner.email.layout
  "Shared branding and document shell for myCarPlanner notification emails."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util Base64]))

(def workshop-address "Sportowa 31, Łódź, Poland")

(def brand-name "myCarPlanner")

(def workshop-hours "06:00–20:00")

;; Matches mycarplanner-frontend index.css + Landing.css
(def ^:private snow "#ffffff")
(def ^:private text-color snow)
(def ^:private accent-bright "#2791f5")
(def ^:private line-border "rgba(255,255,255,0.16)")
(def ^:private page-bg "#0a3d7a")
(def ^:private page-bg-deep "#061525")
(def ^:private page-gradient
  "linear-gradient(165deg, #2791f5 0%, #0d4a94 38%, #061525 100%)")
(def ^:private font-stack
  "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif")

(defn- logo-bytes []
  (with-open [in (io/input-stream (io/resource "email/logo.png"))]
    (.readAllBytes in)))

(defn- logo-data-uri []
  (str "data:image/png;base64,"
       (.encodeToString (Base64/getEncoder) (logo-bytes))))

(defn escape-html [value]
  (-> (str (or value ""))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- blank-line? [x]
  (or (nil? x) (str/blank? (str x))))
(defn- keep-non-blank-lines [lines acc]
  (if (empty? lines)
    (reverse acc)
    (let [line (first lines)]
      (if (blank-line? line)
        (recur (rest lines) acc)
        (recur (rest lines) (conj acc line))))))
(defn- join-fragments [fragments]
  (reduce str "" fragments))
(defn- text-lines [lines]
  (-> (keep-non-blank-lines lines [])
      (str/join "\n")))

(defn wrap-text
  "Plain-text email body with greeting, content lines, and branded closing."
  [customer-name body-lines]
  (let [name (or customer-name "Customer")
        body (if (string? body-lines)
               body-lines
               (text-lines body-lines))]
    (str "Dear " name ",\n\n"
         body
         "\n\n"
         "Service location: " workshop-address "\n\n"
         "Kind regards,\n"
         brand-name)))

(defn- detail-rows-html [pairs]
  (->> pairs
       (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))))
       (map (fn [[label value]]
              (str "<tr>"
                   "<td style=\"padding:6px 12px 6px 0;color:" text-color ";font-size:11px;"
                   "font-weight:600;letter-spacing:0.14em;text-transform:uppercase;vertical-align:top;"
                   "white-space:nowrap;\">"
                   (escape-html label)
                   "</td>"
                   "<td style=\"padding:6px 0;color:" text-color ";font-size:15px;vertical-align:top;\">"
                   (escape-html (str value))
                   "</td>"
                   "</tr>")))
       (join-fragments)))

(defn details-table
  "Optional labeled detail block for HTML emails."
  [pairs]
  (let [rows (detail-rows-html pairs)]
    (when-not (str/blank? rows)
      (str "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
           "style=\"width:100%;margin:18px 0;border-collapse:collapse;\">"
           rows
           "</table>"))))

(defn details-text
  "Labeled detail lines for plain-text emails."
  [pairs]
  (->> pairs
       (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))))
       (map (fn [[label value]] (str label ": " value)))
       (str/join "\n")))

(defn cta-html
  "Primary button + fallback paste link."
  [label url]
  (when (and url (not (str/blank? url)))
    (let [safe-url (escape-html url)
          safe-label (escape-html label)]
      (str "<p style=\"margin:24px 0 8px;\">"
           "<a href=\"" safe-url "\" "
           "style=\"display:inline-block;padding:11px 18px;background:" accent-bright ";"
           "color:" snow ";text-decoration:none;font-size:12px;font-weight:600;"
           "letter-spacing:0.14em;text-transform:uppercase;border:1px solid " accent-bright ";\">"
           safe-label
           "</a></p>"
           "<p style=\"margin:0 0 16px;color:" text-color ";font-size:12px;line-height:1.5;\">"
           "Or paste this link into your browser:<br/>"
           "<span style=\"color:" text-color ";word-break:break-all;\">" safe-url "</span>"
           "</p>"))))

(defn wrap-html
  "Full HTML document — landing-page gradient shell; bgcolor fallbacks for Outlook."
  [customer-name body-html]
  (let [safe-name (escape-html (or customer-name "Customer"))
        shell-style (str "background-color:" page-bg-deep ";background-image:" page-gradient ";"
                         "background-repeat:no-repeat;background-size:cover;")
        logo-uri (logo-data-uri)]
    (str "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
         "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
         "</head>"
         "<body bgcolor=\"" page-bg-deep "\" style=\"margin:0;padding:0;" shell-style
         "font-family:" font-stack ";color:" text-color ";\">"
         "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
         "bgcolor=\"" page-bg-deep "\" "
         "style=\"" shell-style "min-height:100%;\">"
         "<tr><td align=\"center\" bgcolor=\"" page-bg "\" "
         "style=\"padding:24px 16px 32px;background-color:" page-bg ";"
         "background-image:" page-gradient ";\">"
         "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
         "style=\"max-width:560px;\">"
         "<tr><td style=\"padding:0 0 16px;border-bottom:1px solid " line-border ";\">"
         "<img src=\"" logo-uri "\" alt=\"myCarPlanner\" width=\"280\" "
         "style=\"display:block;height:40px;width:auto;max-width:100%;border:0;\"/>"
         "</td></tr>"
         "<tr><td style=\"padding:28px 0 0;\">"
         "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.55;color:" text-color ";\">Dear "
         safe-name ",</p>"
         body-html
         "</td></tr>"
         "<tr><td style=\"padding:28px 0 0;border-top:1px solid " line-border ";\">"
         "<p style=\"margin:0 0 6px;font-size:11px;font-weight:600;letter-spacing:0.14em;"
         "text-transform:uppercase;color:" text-color ";\">Service location</p>"
         "<p style=\"margin:0 0 20px;font-size:15px;line-height:1.5;color:" text-color ";\">"
         (escape-html workshop-address) "</p>"
         "<p style=\"margin:0;font-size:15px;line-height:1.55;color:" text-color ";\">"
         "Kind regards,<br/>"
         (escape-html brand-name)
         "</p>"
         "</td></tr>"
         "</table>"
         "</td></tr></table>"
         "</body></html>")))

(defn p
  "Paragraph fragment for HTML body content."
  [body-text]
  (str "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55;color:" text-color ";\">"
       (escape-html body-text)
       "</p>"))

(defn p-html
  "Paragraph that already contains trusted escaped HTML (e.g. strong)."
  [html]
  (str "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55;color:" text-color ";\">"
       html
       "</p>"))
