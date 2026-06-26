package com.docube.normalize;

public final class DefaultNormalizer implements Normalizer {

    public static final DefaultNormalizer INSTANCE = new DefaultNormalizer();

    private DefaultNormalizer() {
    }

    @Override
    public String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String normalized = input.trim().toUpperCase().replaceAll("\\s+", "");
        return normalizeDashes(normalized);
    }

    private static String normalizeDashes(String value) {
        return value
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-');
    }
}
