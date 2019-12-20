package net.nahknarmi.arch.domain.c4;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

import static java.util.Collections.emptyList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class C4Container implements Relatable {
    @NonNull
    private String name;
    @NonNull
    private String description;
    private String technology;
    private List<C4Component> components = emptyList();
    private List<RelationshipPair> relationships = emptyList();

    @Override
    public List<RelationshipPair> relations() {
        return relationships;
    }
}
