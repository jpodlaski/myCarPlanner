/** Marketing copy for the public site. Specs mirror src/main/resources/hussarya-catalog.json. */
export const HUSSARYA_MODELS = [
    {
        id: "thunder",
        name: "Thunder",
        body: "Supercar",
        since: 2024,
        engines: "V8 560 PS or V10 690 PS",
        drivetrain: "Rear-wheel drive",
        image: "thunder",
        alt: "Hussarya Thunder supercar in dark graphite, seen from the front",
    },
    {
        id: "bastion",
        name: "Bastion",
        body: "SUV",
        since: 2022,
        engines: "V8 560 PS",
        drivetrain: "All-wheel drive",
        image: "bastion",
        alt: "Hussarya Bastion SUV in silver, seen from the front",
    },
    {
        id: "zefir",
        name: "Zefir",
        body: "Roadster",
        since: 2024,
        engines: "V8 560 PS or V10 690 PS",
        drivetrain: "Rear-wheel drive",
        image: "zefir",
        alt: "Hussarya Zefir open-top roadster in silver, seen from the front",
    },
];

export function carImageProps(image, sizes = "(max-width: 833px) 100vw, 33vw") {
    return {
        src: `/img/${image}-1600.jpg`,
        srcSet: `/img/${image}-800.jpg 800w, /img/${image}-1600.jpg 1600w`,
        sizes,
        width: 1600,
        height: 900,
    };
}
