package net.trilogy.arch.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class Diff<T> {
    final private String id;
    final private T before;
    final private T after;
    final private Diff.Status status;

    public enum Status {
        CREATED,
        UPDATED,
        DELETED,
        CHILDREN_UPDATED,
        NO_UPDATE
    }
}
