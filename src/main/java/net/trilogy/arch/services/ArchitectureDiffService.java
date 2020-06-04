package net.trilogy.arch.services;

import com.google.common.collect.Streams;
import net.trilogy.arch.domain.ArchitectureDataStructure;
import net.trilogy.arch.domain.Diff;
import net.trilogy.arch.domain.c4.C4Person;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArchitectureDiffService {
    public static Set<Diff<?>> diff(ArchitectureDataStructure first, ArchitectureDataStructure second) {
        final Stream<C4Person> people = union(first.getModel().getPeople(), second.getModel().getPeople());
        return people
                .map(p1 -> {
                    String id = p1.getId();
                    final C4Person p2 = (C4Person) second.getModel().findEntityById(id).orElse(null);
                    return new Diff<>(
                            p1.getId(),
                            p1,
                            p2,
                            calculateStatus(p1, p2)
                    );
                }).collect(Collectors.toSet());

    }

    private static <T> Stream<T> union(Set<T> first, Set<T> second) {
        return Streams.concat(first.stream(), second.stream());
    }


    private static <T> Diff.Status calculateStatus(T first, T second) {
        if (first == null && second == null) throw new UnsupportedOperationException();
        if (first == null) return Diff.Status.CREATED;
        if (second == null) return Diff.Status.DELETED;
        if (first.equals(second)) return Diff.Status.NO_UPDATE;

        return Diff.Status.UPDATED;
    }
}
