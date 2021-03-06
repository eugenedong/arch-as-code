package net.trilogy.arch.e2e;

import com.structurizr.Workspace;
import net.trilogy.arch.adapter.structurizr.StructurizrAdapter;
import net.trilogy.arch.publish.ArchitectureDataStructurePublisher;
import org.junit.Test;

import java.io.File;

import static net.trilogy.arch.TestHelper.ROOT_PATH_TO_TEST_PRODUCT_DOCUMENTATION;
import static net.trilogy.arch.TestHelper.TEST_WORKSPACE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;

public class ArchitectureDataStructurePublishingE2ETest {

    @Test
    public void should_publish_architecture_data_structure_changes_to_structurizr() throws Exception {
        //given
        File documentationRoot =
                new File(getClass().getResource(ROOT_PATH_TO_TEST_PRODUCT_DOCUMENTATION).getPath());

        //when
        ArchitectureDataStructurePublisher.create(documentationRoot, "product-architecture.yml").publish();

        //then
        StructurizrAdapter adapter = new StructurizrAdapter();
        Workspace workspace = adapter.load(TEST_WORKSPACE_ID);
        assertThat(workspace.getDocumentation().getSections(), hasSize(2));
        assertThat(workspace.getDocumentation().getDecisions(), hasSize(2));
        assertThat(workspace.getModel().getSoftwareSystems(), hasSize(5));
        assertThat(workspace.getModel().getPeople(), hasSize(4));
        assertEquals(getTotalContainerCount(workspace), 4);
        assertEquals(getTotalComponentCount(workspace), 5);
        assertThat(workspace.getModel().getRelationships(), hasSize(22));
    }

    private int getTotalComponentCount(Workspace workspace) {
        return workspace
                .getModel()
                .getSoftwareSystems()
                .stream()
                .map(s -> s.getContainers()
                        .stream()
                        .map(c -> c.getComponents().size())
                        .reduce(0, Integer::sum)
                )
                .reduce(0, Integer::sum);
    }

    private int getTotalContainerCount(Workspace workspace) {
        return workspace
                .getModel()
                .getSoftwareSystems()
                .stream()
                .map(s -> s.getContainers().size())
                .reduce(0, Integer::sum);
    }
}
