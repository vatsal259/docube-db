package com.docube.normalize;

public final class IdentityNormalizer implements Normalizer {

    public static final IdentityNormalizer INSTANCE = new IdentityNormalizer();

    private IdentityNormalizer() {
    }

    @Override
    public String normalize(String input) {
        return input == null ? "" : input;
    }
}
