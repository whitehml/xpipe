package io.xpipe.app.comp.storage.store;

import io.xpipe.app.storage.DataStoreEntry;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public interface StoreSortMode {

    static StoreSortMode ALPHABETICAL_DESC = new StoreSortMode() {
        @Override
        public String getId() {
            return "alphabetical-desc";
        }

        @Override
        public Comparator<StoreSection> comparator() {
            return Comparator.<StoreSection, String>comparing(
                    e -> e.getWrapper().getName().toLowerCase(Locale.ROOT));
        }
    };

    static StoreSortMode ALPHABETICAL_ASC = new StoreSortMode() {
        @Override
        public String getId() {
            return "alphabetical-asc";
        }

        @Override
        public Comparator<StoreSection> comparator() {
            return Comparator.<StoreSection, String>comparing(
                            e -> e.getWrapper().getName().toLowerCase(Locale.ROOT))
                    .reversed();
        }
    };

    static StoreSortMode DATE_DESC = new StoreSortMode() {
        @Override
        public String getId() {
            return "date-desc";
        }

        @Override
        public Comparator<StoreSection> comparator() {
            return Comparator.comparing(e -> {
                return flatten(e)
                        .map(entry -> entry.getLastAccess())
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
            });
        }
    };

    static StoreSortMode DATE_ASC = new StoreSortMode() {
        @Override
        public String getId() {
            return "date-asc";
        }

        @Override
        public Comparator<StoreSection> comparator() {
            return Comparator.<StoreSection, Instant>comparing(e -> {
                return flatten(e)
                        .map(entry -> entry.getLastAccess())
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
            }).reversed();
        }
    };

    static Stream<DataStoreEntry> flatten(StoreSection section) {
        return Stream.concat(
                Stream.of(section.getWrapper().getEntry()),
                section.getChildren().stream().flatMap(section1 -> flatten(section1)));
    }

    static List<StoreSortMode> ALL = List.of(ALPHABETICAL_DESC, ALPHABETICAL_ASC, DATE_DESC, DATE_ASC);

    static Optional<StoreSortMode> fromId(String id) {
        return ALL.stream()
                .filter(storeSortMode -> storeSortMode.getId().equals(id))
                .findFirst();
    }

    String getId();

    Comparator<StoreSection> comparator();
}
