package net.trilogy.arch.commands.architectureUpdate;

import lombok.Getter;
import net.trilogy.arch.adapter.architectureUpdateYaml.ArchitectureUpdateObjectMapper;
import net.trilogy.arch.adapter.architectureYaml.ArchitectureDataStructureObjectMapper;
import net.trilogy.arch.adapter.git.GitInterface;
import net.trilogy.arch.adapter.jira.JiraApi;
import net.trilogy.arch.adapter.jira.JiraApiFactory;
import net.trilogy.arch.adapter.jira.JiraStory.InvalidStoryException;
import net.trilogy.arch.commands.DisplaysErrorMixin;
import net.trilogy.arch.domain.ArchitectureDataStructure;
import net.trilogy.arch.domain.architectureUpdate.ArchitectureUpdate;
import net.trilogy.arch.facade.FilesFacade;
import net.trilogy.arch.services.architectureUpdate.StoryPublishingService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "publish", description = "Publish stories.", mixinStandardHelpOptions = true)
public class AuPublishStoriesCommand implements Callable<Integer>, DisplaysErrorMixin {

    private static final Log logger = LogFactory.getLog(AuPublishStoriesCommand.class);

    private final JiraApiFactory jiraApiFactory;
    private final ArchitectureUpdateObjectMapper architectureUpdateObjectMapper;
    private final FilesFacade filesFacade;
    private final GitInterface gitInterface;

    @CommandLine.Parameters(index = "0", description = "File name of architecture update to validate")
    private File architectureUpdateFileName;

    @CommandLine.Parameters(index = "1", description = "Product architecture root directory")
    private File productArchitectureDirectory;

    @CommandLine.Option(names = {"-b", "--branch-of-base-architecture"}, description = "Name of git branch from which this AU was branched. Used to get names of components. Usually 'master'.", required = true)
    String baseBranch;

    @CommandLine.Option(names = {"-u", "--username"}, description = "Jira username", required = true)
    private String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "Jira password", arity = "0..1", interactive = true, required = true)
    private char[] password;

    @Getter
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    public AuPublishStoriesCommand(JiraApiFactory jiraApiFactory, FilesFacade filesFacade, GitInterface gitInterface) {
        this.jiraApiFactory = jiraApiFactory;
        this.architectureUpdateObjectMapper = new ArchitectureUpdateObjectMapper();
        this.filesFacade = filesFacade;
        this.gitInterface = gitInterface;
    }

    public Integer call() throws IOException, GitAPIException, GitInterface.BranchNotFoundException {
        Path auPath = architectureUpdateFileName.toPath();

        var au = loadAu(auPath);
        if (au.isEmpty()) {
            return 1;
        }

        final Path productArchPath = productArchitectureDirectory.toPath().resolve("product-architecture.yml");
        ArchitectureDataStructure afterAuArchitecture;
        var beforeAuArchitecture = getBeforeAuArchitecture(productArchPath);
        if (beforeAuArchitecture.isEmpty()) return 1;

        try {
            final String archAsString = filesFacade.readString(productArchPath);
            afterAuArchitecture = new ArchitectureDataStructureObjectMapper().readValue(archAsString);
        } catch (Exception e) {
            printError("Unable to load architecture.", e);
            return 1;
        }

        JiraApi jiraApi;
        try {
            jiraApi = jiraApiFactory.create(filesFacade, productArchitectureDirectory.toPath());
        } catch (Exception e) {
            printError("Unable to load configuration.", e);
            return 1;
        }

        final StoryPublishingService jiraService = new StoryPublishingService(
                spec.commandLine().getOut(),
                spec.commandLine().getErr(),
                jiraApi);

        final ArchitectureUpdate updatedAu;
        try {
            updatedAu = jiraService.createStories(au.get(), beforeAuArchitecture.get(), afterAuArchitecture, username, password);
        } catch (JiraApi.JiraApiException e) {
            printError("Jira API failed", e);
            return 1;
        } catch (StoryPublishingService.NoStoriesToCreateException ignored) {
            printError("ERROR: No stories to create.");
            return 1;
        } catch (InvalidStoryException e) {
            printError("ERROR: Some stories are invalid. Please run 'au validate' command.");
            return 1;
        }

        filesFacade.writeString(auPath, architectureUpdateObjectMapper.writeValueAsString(updatedAu));

        return 0;
    }

    private Optional<ArchitectureDataStructure> getBeforeAuArchitecture(Path productArchPath) {
        try {
            return Optional.of(gitInterface.load(baseBranch, productArchPath));
        } catch (Exception e) {
            printError("Unable to load product architecture in branch: " + baseBranch, e);
            return Optional.empty();
        }
    }

    private Optional<ArchitectureUpdate> loadAu(Path auPath) {
        try {
            return Optional.of(architectureUpdateObjectMapper.readValue(filesFacade.readString(auPath)));
        } catch (Exception e) {
            printError("Unable to load architecture update.", e);
            return Optional.empty();
        }
    }
}

