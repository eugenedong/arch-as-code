package net.trilogy.arch.domain.c4;

import lombok.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static net.trilogy.arch.domain.c4.C4Location.UNSPECIFIED;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class C4SoftwareSystem extends BaseEntity implements Entity, HasLocation {
    private C4Location location;

    @Builder
    C4SoftwareSystem(String id, String alias, String name, C4Path path, String description, @Singular Set<C4Tag> tags, @Singular List<C4Relationship> relationships, C4Location location) {
        super(id, alias, path, name, description, tags, relationships);
        this.location = Optional.ofNullable(location).orElse(UNSPECIFIED);
    }

    public C4Type getType() {
        return C4Type.system;
    }

    public static class C4SoftwareSystemBuilder {
        public C4SoftwareSystemBuilder path(C4Path path) {
            checkArgument(C4Type.system.equals(path.type()), format("Path %s is not valid for SoftwareSystem.", path));
            this.path = path;
            return this;
        }
    }
}
