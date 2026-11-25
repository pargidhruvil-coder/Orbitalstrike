package com.orbitalstrike;

/** सर्वर और क्लाइंट दोनों एक ही टाइमलाइन (टिक में) इस्तेमाल करते हैं */
public final class Timeline {
    public static final int LONG = 0, MEDIUM = 1, SHORT = 2;
    public static final int PLANETS = 9;

    public final int mode;
    public final int planetsStart;   // ग्रह आना शुरू
    public final int planetStep;     // हर ग्रह के बीच का अंतर
    public final int planetFly;      // एक ग्रह को उड़कर आने में
    public final int alignedAt;      // "नवग्रह संरेखित"
    public final int vajraStart;     // इंद्र + वज्र (-1 = नहीं)
    public final int impact;         // प्रहार
    public final int end;            // सिनेमैटिक खत्म

    private Timeline(int mode, int planetsStart, int planetStep, int planetFly, int vajraStart, int impact, int end) {
        this.mode = mode;
        this.planetsStart = planetsStart;
        this.planetStep = planetStep;
        this.planetFly = planetFly;
        this.alignedAt = planetsStart + planetStep * (PLANETS - 1) + planetFly;
        this.vajraStart = vajraStart;
        this.impact = impact;
        this.end = end;
    }

    public static Timeline of(int mode) {
        return switch (mode) {
            case LONG -> new Timeline(LONG, 780, 22, 30, 1060, 1260, 1440);
            case MEDIUM -> new Timeline(MEDIUM, 0, 10, 24, 140, 270, 410);
            default -> new Timeline(SHORT, 0, 4, 22, -1, 80, 150);
        };
    }

    public int planetArrive(int i) {
        return planetsStart + i * planetStep + planetFly;
    }
}
