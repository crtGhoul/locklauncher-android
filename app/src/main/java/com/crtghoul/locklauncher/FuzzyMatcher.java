package com.crtghoul.locklauncher;

/**
 * Fast, allocation-efficient fuzzy search matcher for app launcher queries.
 * Supports:
 * 1. Exact substring matches (highest priority)
 * 2. Word prefix matches (e.g. "gm" matches "Google Maps", "yt" matches "YouTube")
 * 3. Subsequence matches (e.g. "term" matches "Terminal")
 */
public class FuzzyMatcher {

    public static int matchScore(String target, String query) {
        if (target == null || query == null) return -1;
        String t = target.trim().toLowerCase();
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return 100;
        if (t.isEmpty()) return -1;

        // 1. Exact start match (highest score)
        if (t.startsWith(q)) {
            return 1000 - (t.length() - q.length());
        }

        // 2. Substring match
        int subIdx = t.indexOf(q);
        if (subIdx >= 0) {
            return 800 - subIdx - (t.length() - q.length());
        }

        // 3. Word acronym / initials match (e.g. "ps" for "Play Store", "yt" for "YouTube")
        String[] words = t.split("[\\s_\\-\\.]+");
        StringBuilder acronym = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) acronym.append(w.charAt(0));
        }
        if (acronym.toString().startsWith(q)) {
            return 700;
        }

        // Also check camelCase initials (e.g. "YouTube" -> "yt", "ProtonVPN" -> "pvpn")
        StringBuilder camel = new StringBuilder();
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (Character.isUpperCase(c) || (i == 0 && Character.isLetter(c))) {
                camel.append(Character.toLowerCase(c));
            }
        }
        if (camel.toString().startsWith(q)) {
            return 650;
        }

        // 4. Subsequence fuzzy match (all chars of query appear in order in target)
        int tIdx = 0;
        int qIdx = 0;
        int score = 500;
        while (tIdx < t.length() && qIdx < q.length()) {
            if (t.charAt(tIdx) == q.charAt(qIdx)) {
                qIdx++;
                score += 5;
            }
            tIdx++;
        }
        if (qIdx == q.length()) {
            return score - (t.length() - q.length());
        }

        return -1; // No match
    }
}
