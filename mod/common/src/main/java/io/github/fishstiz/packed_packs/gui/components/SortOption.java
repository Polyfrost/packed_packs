package io.github.fishstiz.packed_packs.gui.components;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public enum SortOption {
    NONE, A_Z, Z_A, RECENT, OLDEST;

    public String translationKey() {
        return "packed_packs.sort." + this.name().toLowerCase(Locale.ROOT);
    }

    public SortOption next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public <T> @Nullable Comparator<T> comparator(Function<T, String> name, ToLongFunction<T> lastModified) {
        Comparator<T> byName = Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER);
        Comparator<T> byDate = Comparator.comparingLong(lastModified);
        return switch (this) {
            case NONE -> null;
            case A_Z -> byName;
            case Z_A -> byName.reversed();
            case RECENT -> byDate.reversed();
            case OLDEST -> byDate;
        };
    }
}
