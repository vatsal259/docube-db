package com.docube.scoring;

public class LevenshteinScorer implements SimilarityScorer {

    @Override
    public double score(String query, String record) {
        return normalizedSimilarity(query, record);
    }

    @Override
    public double scoreChunks(String queryChunk, String recordChunk) {
        return normalizedSimilarity(queryChunk, recordChunk);
    }

    private static double normalizedSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLen);
    }

    static int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[b.length()];
    }
}
