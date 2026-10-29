package com.crtghoul.locklauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Memoized fuzzy search + math evaluation, Android-native.
 *
 * <p>Honest performance statement, up front: a cache hit is fast, but
 * a first-time fuzzy score over hundreds of apps is NOT sub-millisecond
 * and must not be claimed as such. FuzzyMatcher.matchScore does
 * toLowerCase + trim + substring + acronym + camel + subsequence
 * scans per target; over ~200 apps that is thousands of character
 * operations. What this class actually guarantees:
 * - Repeat queries (same normalized query, same target list) return
 *   from an LRU cache with no rescoring.
 * - Queries under 3 characters use a precomputed prefix index built
 *   once per target list, so they never run the full fuzzy scorer.
 * - No allocation-heavy work is added on the UI thread beyond the
 *   cache lookup itself; the first build of the index happens once,
 *   when targets are set, and callers should set targets off the
 *   critical path if the list is large.
 *
 * <p>MathEvaluator is NOT edited here. The security-fix pass already
 * owns its StackOverflowError fix (deep parenthesis nesting recurses
 * in Parser.parseFactor and evaluate() catches only Exception).
 * This class wraps it with the guard that fix will also need:
 * queries longer than 200 chars or with parenthesis depth over 32
 * are rejected as invalid without parsing. If the security pass
 * lands the same guard inside MathEvaluator, this wrapper's guard
 * becomes redundant but harmless.
 */
public final class MemoizedSearch {

    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int MAX_MATH_CACHE_ENTRIES = 128;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_PAREN_DEPTH = 32;

    /** One scored result, sorted highest score first by callers. */
    public static final class Result {
        public final String target;
        public final int score;

        public Result(String target, int score) {
            this.target = target;
            this.score = score;
        }
    }

    private final List<String> targets = new ArrayList<>();
    // Prefix index: 1-char and 2-char word-start prefixes -> target indices.
    private final Map<String, List<Integer>> prefixIndex =
            new LinkedHashMap<>();
    private final Map<String, List<Result>> queryCache =
            new LinkedHashMap<String, List<Result>>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, List<Result>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };
    private final Map<String, MathEvaluator.Result> mathCache =
            new LinkedHashMap<String, MathEvaluator.Result>(
                    MAX_MATH_CACHE_ENTRIES, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, MathEvaluator.Result> eldest) {
                    return size() > MAX_MATH_CACHE_ENTRIES;
                }
            };

    /** Set (or replace) the target list; rebuilds the index, clears caches. */
    public synchronized void setTargets(List<String> newTargets) {
        targets.clear();
        prefixIndex.clear();
        queryCache.clear();
        if (newTargets == null) return;
        targets.addAll(newTargets);
        for (int i = 0; i < targets.size(); i++) {
            String t = targets.get(i);
            if (t == null) continue;
            String lower = t.trim().toLowerCase(Locale.ROOT);
            if (lower.isEmpty()) continue;
            // Index by first char and first two chars of each word.
            String[] words = lower.split("[\\s_\\-\\.]+");
            for (String w : words) {
                if (w.isEmpty()) continue;
                addToIndex(w.substring(0, 1), i);
                if (w.length() >= 2) {
                    addToIndex(w.substring(0, 2), i);
                }
            }
        }
    }

    private void addToIndex(String key, int targetIndex) {
        List<Integer> list = prefixIndex.get(key);
        if (list == null) {
            list = new ArrayList<>();
            prefixIndex.put(key, list);
        }
        if (!list.contains(targetIndex)) list.add(targetIndex);
    }

    /**
     * Search targets for a query, highest score first.
     * Fast path (query length 1-2, after trim/lowercase): prefix-index
     * lookup only, no FuzzyMatcher scoring loop. All other queries:
     * full FuzzyMatcher scoring, then cached under the normalized query.
     */
    public synchronized List<Result> search(String rawQuery) {
        if (rawQuery == null) return Collections.emptyList();
        String q = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return Collections.emptyList();
        List<Result> cached = queryCache.get(q);
        if (cached != null) return new ArrayList<>(cached);

        List<Result> out = new ArrayList<>();
        if (q.length() < 3) {
            List<Integer> indices = prefixIndex.get(q);
            if (indices != null) {
                for (Integer idx : indices) {
                    if (idx >= 0 && idx < targets.size()) {
                        // Prefix hits are exact word-start matches: score
                        // them in the substring tier so ordering stays
                        // comparable with full fuzzy results.
                        out.add(new Result(targets.get(idx), 800));
                    }
                }
            }
        } else {
            for (String target : targets) {
                if (target == null) continue;
                int score = FuzzyMatcher.matchScore(target, q);
                if (score >= 0) out.add(new Result(target, score));
            }
            Collections.sort(out, new Comparator<Result>() {
                @Override public int compare(Result a, Result b) {
                    return b.score - a.score;
                }
            });
        }
        queryCache.put(q, new ArrayList<>(out));
        return out;
    }

    /** Math evaluation with cache + depth/length guard (see class doc). */
    public synchronized MathEvaluator.Result evaluateMath(String input) {
        if (input == null) return new MathEvaluator.Result(false, 0, "");
        String key = input.trim();
        MathEvaluator.Result cached = mathCache.get(key);
        if (cached != null) return cached;
        if (key.length() > MAX_QUERY_LENGTH || parenDepth(key) > MAX_PAREN_DEPTH) {
            MathEvaluator.Result invalid =
                    new MathEvaluator.Result(false, 0, "");
            mathCache.put(key, invalid);
            return invalid;
        }
        MathEvaluator.Result result;
        try {
            result = MathEvaluator.evaluate(input);
        } catch (StackOverflowError e) {
            // Belt-and-braces until MathEvaluator itself caps depth.
            result = new MathEvaluator.Result(false, 0, "");
        } catch (Exception ignored) {
            result = new MathEvaluator.Result(false, 0, "");
        }
        mathCache.put(key, result);
        return result;
    }

    private static int parenDepth(String s) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
                if (depth > max) max = depth;
            } else if (c == ')') {
                depth--;
            }
        }
        return max;
    }

    public synchronized void clear() {
        queryCache.clear();
        mathCache.clear();
    }

    public synchronized int targetCount() {
        return targets.size();
    }
}
