package net.trilogy.arch.services;

import net.trilogy.arch.domain.ArchitectureDataStructure;
import net.trilogy.arch.domain.Diff;
import net.trilogy.arch.domain.c4.*;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static net.trilogy.arch.ArchitectureDataStructureHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

public class ArchitectureDiffServiceTest {

    @Test
    public void shouldDiffEmptyArchitectures() {
        final ArchitectureDataStructure first = emptyArch().build();
        final ArchitectureDataStructure second = emptyArch().build();

        assertThat(ArchitectureDiffService.diff(first, second), equalTo(Set.of()));
    }

    @Test
    public void shouldDiffPeopleEntities() {
        var arch = emptyArch();
        final C4Person personInFirst = createPerson("1");
        final C4Person personInSecond = createPerson("3");
        final C4Person commonPersonNameToBeChanged = createPerson("2");
        final C4Person commonPersonNameChanged = createPerson("2");
        commonPersonNameChanged.setName("new-name");
        final C4Person commonPersonNoChange = createPerson("4");

        var first = getArchWithPeople(arch, Set.of(personInFirst, commonPersonNameToBeChanged, commonPersonNoChange));
        var second = getArchWithPeople(arch, Set.of(personInSecond, commonPersonNameChanged, commonPersonNoChange));
        Set<Diff<?>> expected = Set.of(
                new Diff<C4Person>(personInFirst.getId(), personInFirst, null, Diff.Status.DELETED),
                new Diff<C4Person>(personInSecond.getId(), null, personInSecond, Diff.Status.CREATED),
                new Diff<C4Person>(commonPersonNoChange.getId(), commonPersonNoChange, commonPersonNoChange, Diff.Status.NO_UPDATE),
                new Diff<C4Person>(commonPersonNameToBeChanged.getId(), commonPersonNameToBeChanged, commonPersonNameChanged, Diff.Status.UPDATED)
        );

        assertThat(ArchitectureDiffService.diff(first, second), equalTo(expected));
    }

    @Ignore("wip")
    @Test
    public void shouldDiffPeopleRelationships() {
        var arch = emptyArch();
        final C4SoftwareSystem system2 = createSystem("2");
        final C4SoftwareSystem system3 = createSystem("3");
        final Set<C4SoftwareSystem> systems = Set.of(system2, system3);

        final C4Person personWithRelationshipsToSystem2 = createPersonWithRelationshipsTo("1", Set.of(system2));
        final C4Person personWithRelationshipsToSystem3 = createPersonWithRelationshipsTo("1", Set.of(system3));

        var first = getArch(arch, Set.of(personWithRelationshipsToSystem2), systems, Set.of(), Set.of(), Set.of());
        var second = getArch(arch, Set.of(personWithRelationshipsToSystem3), systems, Set.of(), Set.of(), Set.of());

        List<Diff<?>> expected = null;
        fail("wip");

        assertThat(ArchitectureDiffService.diff(first, second), equalTo(expected));
    }

    @Ignore("wip")
    @Test
    public void shouldDiffSystemsEntities() {
        var arch = emptyArch();
        C4SoftwareSystem commonSystem = createSystem("2");
        C4SoftwareSystem systemInFirst = createSystem("1");
        C4SoftwareSystem systemInSecond = createSystem("3");

        var first = getArchWithSystems(arch, Set.of(systemInFirst, commonSystem));
        var second = getArchWithSystems(arch, Set.of(systemInSecond, commonSystem));

        List<Diff<?>> expected = null;
        fail("wip");

        assertThat(ArchitectureDiffService.diff(first, second), equalTo(expected));
    }

    @Ignore("wip")
    @Test
    public void shouldDiffSystemRelationships() {
        var arch = emptyArch();
        final C4SoftwareSystem system2 = createSystem("2");
        final C4SoftwareSystem system3 = createSystem("3");
        final Set<C4SoftwareSystem> commonSystems = Set.of(system2, system3);

        C4SoftwareSystem systemWithRelationshipToSystem2 = createSystemWithRelationshipsTo("1", Set.of(system2));
        C4SoftwareSystem systemWithRelationshipToSystem3 = createSystemWithRelationshipsTo("1", Set.of(system3));

        var first = getArchWithSystems(arch, Set.of(systemWithRelationshipToSystem2, system2, system3));
        var second = getArchWithSystems(arch, Set.of(systemWithRelationshipToSystem3, system2, system3));

        List<Diff<?>> expected = null;
        fail("wip");

        assertThat(ArchitectureDiffService.diff(first, second), equalTo(expected));
    }

    private ArchitectureDataStructure getArchWithPeople(ArchitectureDataStructure.ArchitectureDataStructureBuilder arch, Set<C4Person> people) {
        return arch.model(
                new C4Model(people, Set.of(), Set.of(), Set.of(), Set.of()
                )
        ).build();
    }

    private ArchitectureDataStructure getArchWithSystems(ArchitectureDataStructure.ArchitectureDataStructureBuilder arch, Set<C4SoftwareSystem> systems) {
        return arch.model(
                new C4Model(Set.of(), systems, Set.of(), Set.of(), Set.of()
                )
        ).build();
    }

    private ArchitectureDataStructure getArch(ArchitectureDataStructure.ArchitectureDataStructureBuilder arch,
                                              Set<C4Person> people,
                                              Set<C4SoftwareSystem> systems,
                                              Set<C4Container> containers,
                                              Set<C4Component> components,
                                              Set<C4DeploymentNode> deploymentNodes) {
        return arch.model(
                new C4Model(people, systems, containers, components, deploymentNodes)
        ).build();
    }
}
