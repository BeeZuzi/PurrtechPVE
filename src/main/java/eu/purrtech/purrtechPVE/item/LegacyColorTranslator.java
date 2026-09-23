package eu.purrtech.purrtechPVE.item;

import java.util.Locale;

/**
 * Rewrites legacy {@code &}/{@code §} color codes (incl. {@code &x&R&R&G&G&B&B} and {@code &#RRGGBB}
 * hex) into equivalent MiniMessage tags, so admin text pasted from other plugins keeps its colors
 * and can still be mixed with regular MiniMessage tags in the same line.
 */
final class LegacyColorTranslator {

    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private LegacyColorTranslator() {
    }

    static String toMiniMessage(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        // Legacy semantics: a color code clears bold/italic/etc. that came before it, MiniMessage
        // color tags don't - so a <reset> is emitted before a color only when a format is active.
        boolean formatActive = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c != '&' && c != '§') || i + 1 >= input.length()) {
                out.append(c);
                i++;
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));

            String hex = null;
            int consumed = 0;
            if (code == 'x') {
                hex = spacedHex(input, i + 2);
                consumed = 14;
            } else if (code == '#') {
                hex = plainHex(input, i + 2);
                consumed = 8;
            }
            if (hex != null) {
                if (formatActive) {
                    out.append("<reset>");
                    formatActive = false;
                }
                out.append("<#").append(hex).append('>');
                i += consumed;
                continue;
            }

            int colorIndex = Character.digit(code, 16);
            if (colorIndex >= 0) {
                if (formatActive) {
                    out.append("<reset>");
                    formatActive = false;
                }
                out.append('<').append(COLOR_NAMES[colorIndex]).append('>');
                i += 2;
                continue;
            }

            String format = switch (code) {
                case 'k' -> "obfuscated";
                case 'l' -> "bold";
                case 'm' -> "strikethrough";
                case 'n' -> "underlined";
                case 'o' -> "italic";
                case 'r' -> "reset";
                default -> null;
            };
            if (format == null) {
                out.append(c);
                i++;
                continue;
            }
            out.append('<').append(format).append('>');
            formatActive = code != 'r';
            i += 2;
        }
        return out.toString();
    }

    /** Parses the {@code &R&R&G&G&B&B} tail of {@code &x...} starting at {@code start}, or null if malformed. */
    private static String spacedHex(String input, int start) {
        if (start + 12 > input.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int j = 0; j < 6; j++) {
            char marker = input.charAt(start + j * 2);
            char digit = input.charAt(start + j * 2 + 1);
            if ((marker != '&' && marker != '§') || Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString().toLowerCase(Locale.ROOT);
    }

    private static String plainHex(String input, int start) {
        if (start + 6 > input.length()) {
            return null;
        }
        String hex = input.substring(start, start + 6);
        for (int j = 0; j < 6; j++) {
            if (Character.digit(hex.charAt(j), 16) < 0) {
                return null;
            }
        }
        return hex.toLowerCase(Locale.ROOT);
    }
}
