package io.github.brooswitminecraft.dynamicatmosphere.engine.event;

/**
 * A single data-defined, material-agnostic threshold check (spec section 26:
 * "threshold bands are... multiple data-defined rules with different conditions and
 * probabilities. NOT hardcoded bands."). Every built-in condition kind lives here;
 * adding a new material behavior should almost never require a new {@code Condition}
 * subtype, only a new combination of existing ones.
 */
public sealed interface Condition {
    boolean matches(EventContext context);

    record ConcentrationAtLeast(double min) implements Condition {
        public ConcentrationAtLeast {
            if (!Double.isFinite(min) || min < 0 || min > 1) {
                throw new IllegalArgumentException("min must be within [0, 1]");
            }
        }

        @Override
        public boolean matches(EventContext context) {
            return context.concentration() >= min;
        }
    }

    record ConcentrationAtMost(double max) implements Condition {
        public ConcentrationAtMost {
            if (!Double.isFinite(max) || max < 0 || max > 1) {
                throw new IllegalArgumentException("max must be within [0, 1]");
            }
        }

        @Override
        public boolean matches(EventContext context) {
            return context.concentration() <= max;
        }
    }

    record Precipitating(boolean expected) implements Condition {
        @Override
        public boolean matches(EventContext context) {
            return context.precipitating() == expected;
        }
    }

    record BiomeTemperatureAtLeast(double min) implements Condition {
        public BiomeTemperatureAtLeast {
            if (!Double.isFinite(min)) throw new IllegalArgumentException("min must be finite");
        }

        @Override
        public boolean matches(EventContext context) {
            return context.biomeTemperature() >= min;
        }
    }

    record BiomeTemperatureAtMost(double max) implements Condition {
        public BiomeTemperatureAtMost {
            if (!Double.isFinite(max)) throw new IllegalArgumentException("max must be finite");
        }

        @Override
        public boolean matches(EventContext context) {
            return context.biomeTemperature() <= max;
        }
    }

    static boolean allMatch(Iterable<Condition> conditions, EventContext context) {
        for (Condition condition : conditions) {
            if (!condition.matches(context)) return false;
        }
        return true;
    }
}
