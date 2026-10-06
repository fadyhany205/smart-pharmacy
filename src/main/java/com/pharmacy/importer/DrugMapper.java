package com.pharmacy.importer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives pharmacy fields from a drug record: dosage form, smallest unit, pack structure (strips × units),
 * strength, main category group and a readable name. Pure logic, no database access.
 */
public class DrugMapper {

    /** Smallest unit kinds (the import creates the matching Unit rows when missing). */
    public enum UnitKind {
        TABLET("قرص", "Tablet", true), CAPSULE("كبسولة", "Capsule", true), SACHET("كيس", "Sachet", true),
        AMPOULE("أمبول", "Ampoule", true), VIAL("فيال", "Vial", true), SUPPOSITORY("لبوس", "Suppository", true),
        SYRINGE("سرنجة", "Syringe", true), BOTTLE("زجاجة", "Bottle", false), TUBE("أنبوب", "Tube", false),
        PIECE("قطعة", "Piece", false);

        public final String ar, en;
        public final boolean countable;

        UnitKind(String ar, String en, boolean countable) { this.ar = ar; this.en = en; this.countable = countable; }
    }

    /** Main categories (created when missing). */
    public enum Group {
        MEDICINES("أدوية", "Medicines"), COSMETICS("مستحضرات تجميل", "Cosmetics"),
        SUPPLEMENTS("مكملات غذائية", "Supplements"), BABY_MILK("ألبان أطفال", "Baby milk"),
        SUPPLIES("مستلزمات طبية", "Medical supplies");

        public final String ar, en;

        Group(String ar, String en) { this.ar = ar; this.en = en; }
    }

    public record Mapped(String nameEn, String nameAr, String strength, String dosageForm, UnitKind unit,
                         int stripsPerBox, int unitsPerStrip, Group group) {}

    private static final Pattern COUNT = Pattern.compile(
            "(\\d{1,4})\\s*(?:(?:f\\.?\\s*c\\.?|s\\.?\\s*c\\.?|e\\.?\\s*c\\.?|sugar\\s+coated|film\\s+coated|chew\\.?|chewable|eff\\.?|effervescent|"
                    + "scored|soft\\s+gelatin|orodispersible|dispersible|vaginal|oral|x\\.?r\\.?|s\\.?r\\.?|m\\.?r\\.?|d\\.?r\\.?|i\\.?v\\.?|i\\.?m\\.?)\\s*)*"
                    + "(tabs?|tablets?|caps?|capsules?|sachets?|amps?|ampoules?|vials?|supp\\w*|lozenges?|prefilled\\s+syringes?|syringes?)\\b");
    private static final Pattern STRENGTH = Pattern.compile(
            "(\\d+(?:\\.\\d+)?(?:\\s*/\\s*\\d+(?:\\.\\d+)?)*)\\s*(mg|mcg|gm|g|ml|i\\.?u\\.?|iu|%)(?:\\s*/\\s*(\\d+(?:\\.\\d+)?)?\\s*ml)?(?![a-z])");
    private static final Pattern VOLUME = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ml|gm|g)(?![a-z])");

    private final boolean guessStrips;

    /** guessStrips: when the source has no strips count, assume strips of 10 (or 7) tablets/capsules. */
    public DrugMapper(boolean guessStrips) {
        this.guessStrips = guessStrips;
    }

    public Mapped map(DrugRecord r) {
        String en = r.nameEn() == null ? "" : r.nameEn();
        String low = en.toLowerCase(Locale.ROOT);
        String[] form = formOf(low, r.route());
        UnitKind unit = UnitKind.valueOf(form[1]);

        Integer count = null;
        Matcher cm = COUNT.matcher(low);
        if (cm.find()) {
            int n = Integer.parseInt(cm.group(1));
            if (n >= 1 && n <= 1000) count = n;
        }
        int strips = 1, perStrip = 1;
        Integer sub = r.subUnits();
        if (unit.countable && count != null) {
            if (sub != null && sub > 1 && count % sub == 0) { strips = sub; perStrip = count / sub; }
            else if (sub == null && guessStrips && (unit == UnitKind.TABLET || unit == UnitKind.CAPSULE) && count >= 14) {
                int size = count % 10 == 0 ? 10 : count % 7 == 0 ? 7 : count % 12 == 0 ? 12 : 0;
                if (size > 0) { strips = count / size; perStrip = size; } else { perStrip = count; }
            }
            else perStrip = count;
        } else if (sub != null && sub > 1) {
            strips = sub;     // e.g. 10 ampoules / 20 bottles in a box: sell each one
        }

        String strength = null;
        Matcher sm = STRENGTH.matcher(low);
        if (sm.find()) strength = sm.group().replaceAll("\\s+", "");

        String nameAr = arabicName(r.nameAr(), strength, count, unit, low);
        return new Mapped(pretty(en), nameAr, cut(strength, 50), form[0], unit, strips, perStrip, groupOf(r.therapeuticClass(), low));
    }

    /** [dosage form (Arabic), unit kind]. */
    static String[] formOf(String n, String route) {
        if (has(n, "eff")) return new String[]{"فوار", has(n, "sachet") ? "SACHET" : "TABLET"};
        if (has(n, "sachet")) return new String[]{"أكياس", "SACHET"};
        if (has(n, "supp")) return new String[]{"لبوس", "SUPPOSITORY"};
        if (has(n, "amp")) return new String[]{"حقن", "AMPOULE"};
        if (has(n, "vial")) return new String[]{"حقن", "VIAL"};
        if (has(n, "syringe")) return new String[]{"حقن", "SYRINGE"};
        if (n.matches(".*\\bcap(s|sule|sules)?\\b.*")) return new String[]{"كبسولات", "CAPSULE"};
        if (n.matches(".*\\b(tabs?|tablets?|lozenges?)\\b.*") || n.contains("f.c.t")) return new String[]{"أقراص", "TABLET"};
        if (has(n, "syrup")) return new String[]{"شراب", "BOTTLE"};
        if (has(n, "susp")) return new String[]{"معلق", "BOTTLE"};
        if (has(n, "drop")) return new String[]{"نقط", "BOTTLE"};
        if (has(n, "spray") || has(n, "inhaler")) return new String[]{"بخاخ", "BOTTLE"};
        if (has(n, "cream")) return new String[]{"كريم", "TUBE"};
        if (has(n, "oint")) return new String[]{"مرهم", "TUBE"};
        if (n.matches(".*\\bgel\\b.*")) return new String[]{"جل", "TUBE"};
        if (has(n, "lotion")) return new String[]{"لوشن", "BOTTLE"};
        if (has(n, "shampoo")) return new String[]{"شامبو", "BOTTLE"};
        if (has(n, "serum")) return new String[]{"سيرم", "BOTTLE"};
        if (has(n, "wash")) return new String[]{"غسول", "BOTTLE"};
        if (n.matches(".*\\b(inf\\.?|i\\.?v\\.?|infusion)\\b.*")) return new String[]{"محلول وريدي", "BOTTLE"};
        if (n.matches(".*\\b(sol\\.|soln\\.?|solution)\\b.*")) return new String[]{"محلول", "BOTTLE"};
        if (n.matches(".*\\b(powder|pd\\.|pwd)\\b.*")) return new String[]{"بودرة", "PIECE"};
        if (has(n, "soap")) return new String[]{"صابون", "PIECE"};
        String r = route == null ? "" : route.toUpperCase(Locale.ROOT);
        return switch (r) {
            case "ORAL.SOLID" -> new String[]{"أقراص", "TABLET"};
            case "ORAL.LIQUID" -> new String[]{"شراب", "BOTTLE"};
            case "INJECTION" -> new String[]{"حقن", "AMPOULE"};
            case "EFF" -> new String[]{"فوار", "SACHET"};
            case "SPRAY" -> new String[]{"بخاخ", "BOTTLE"};
            case "EYE" -> new String[]{"قطرة عين", "BOTTLE"};
            case "EAR" -> new String[]{"قطرة أذن", "BOTTLE"};
            case "RECTAL" -> new String[]{"لبوس", "SUPPOSITORY"};
            case "VAGINAL" -> new String[]{"مهبلي", "PIECE"};
            case "TOPICAL" -> new String[]{"موضعي", "TUBE"};
            case "SOAP" -> new String[]{"صابون", "PIECE"};
            default -> new String[]{null, "PIECE"};
        };
    }

    static Group groupOf(String cls, String name) {
        String c = cls == null ? "" : cls.toLowerCase(Locale.ROOT);
        String s = c.isEmpty() ? name : c;
        if (s.contains("milk")) return Group.BABY_MILK;
        if (s.matches(".*(hair|skin care|sun ?block|sunscreen|spf|whiten|moistur|massage|oral care|cleans|soap|shampoo|deodor|"
                + "perfume|lip|make ?up|beauty|body|facial|serum|anti-?aging|cosmetic|nail|intimate|feminine wash|vaginal wash|baby care).*"))
            return Group.COSMETICS;
        if (s.matches(".*(vitamin|supplement|omega|tonic|antioxidant|weight loss|herbal|probiotic|protein|energy|nutrition|"
                + "iron supp|calcium supp|zinc).*"))
            return Group.SUPPLEMENTS;
        if (s.matches(".*(device|supplies|diaper|bandage|dressing|gauze|condom|test strip|pregnancy test|thermometer|lens|"
                + "glucometer|syringe only|medical tool).*"))
            return Group.SUPPLIES;
        return Group.MEDICINES;
    }

    /**
     * Keeps a real Arabic name as is. A transliterated name without any size (no digits) gets the strength and
     * pack from the English name, so "بانادول إكسترا" 24 and 48 tablets stay distinguishable.
     */
    static String arabicName(String ar, String strength, Integer count, UnitKind unit, String low) {
        String base = ar == null || ar.isBlank() ? null : ar.trim();
        if (base != null && base.matches(".*\\d.*")) return cut(base, 200);
        StringBuilder suffix = new StringBuilder();
        if (strength != null) suffix.append(' ').append(arabicUnits(strength));
        if (count != null && unit.countable) suffix.append(' ').append(count).append(' ').append(unit.ar);
        else if (strength == null) {
            Matcher v = VOLUME.matcher(low);
            if (v.find()) suffix.append(' ').append(arabicUnits(v.group().replaceAll("\\s+", "")));
        }
        String result = (base == null ? "" : base) + suffix;
        return result.isBlank() ? null : cut(result.trim(), 200);
    }

    static String arabicUnits(String s) {
        return s.replace("mcg", " ميكروجرام").replace("mg", " مجم").replace("gm", " جم").replaceAll("(?<=\\d)g\\b", " جم")
                .replace("ml", " مل").replaceAll("i\\.?u\\.?", " وحدة").replaceAll("\\s+", " ").trim();
    }

    /** "PANADOL EXTRA 24 F.C. TABS." / "panadol extra 24 f.c. tabs" -> "Panadol Extra 24 F.C. Tabs." (units stay lower-case). */
    static String pretty(String s) {
        if (s == null || s.isBlank()) return null;
        boolean allUpper = s.equals(s.toUpperCase(Locale.ROOT)), allLower = s.equals(s.toLowerCase(Locale.ROOT));
        if (!allUpper && !allLower) return cut(s.trim(), 200);
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!out.isEmpty()) out.append(' ');
            if (w.matches("\\d*(mg|mcg|gm|g|ml|kg|iu)(/\\d*ml)?")) out.append(w);
            else if (w.contains(".") && w.length() <= 6) out.append(w.toUpperCase(Locale.ROOT));
            else out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return cut(out.toString(), 200);
    }

    private static boolean has(String n, String word) {
        return n.matches(".*\\b" + Pattern.quote(word) + ".*");
    }

    static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
