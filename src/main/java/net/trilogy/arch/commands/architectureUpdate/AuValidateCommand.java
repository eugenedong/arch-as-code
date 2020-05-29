package net.trilogy.arch.commands.architectureUpdate;

import net.trilogy.arch.adapter.architectureUpdateYaml.ArchitectureUpdateObjectMapper;
import net.trilogy.arch.facade.FilesFacade;
import net.trilogy.arch.facade.GitFacade;
import net.trilogy.arch.adapter.architectureYaml.ArchitectureDataStructureObjectMapper;
import net.trilogy.arch.adapter.architectureYaml.ArchitectureDataStructureReader;
import net.trilogy.arch.adapter.architectureYaml.GitBranchReader;
import net.trilogy.arch.adapter.architectureYaml.GitBranchReader.BranchNotFoundException;
import net.trilogy.arch.commands.DisplaysErrorMixin;
import net.trilogy.arch.domain.ArchitectureDataStructure;
import net.trilogy.arch.domain.architectureUpdate.ArchitectureUpdate;
import net.trilogy.arch.validation.architectureUpdate.ArchitectureUpdateValidator;
import net.trilogy.arch.validation.architectureUpdate.ValidationError;
import net.trilogy.arch.validation.architectureUpdate.ValidationErrorType;
import net.trilogy.arch.validation.architectureUpdate.ValidationResult;
import net.trilogy.arch.validation.architectureUpdate.ValidationStage;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;

import lombok.Getter;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Parameters;
import static picocli.CommandLine.Spec;

@Command(name = "validate", description = "Validate Architecture Update", mixinStandardHelpOptions = true)
public class AuValidateCommand implements Callable<Integer>, DisplaysErrorMixin {

    @Parameters(index = "0", description = "File name of architecture update to validate")
    private File architectureUpdateFilePath;

    @Parameters(index = "1", description = "Product architecture root directory")
    private File productArchitectureDirectory;

    @CommandLine.Option(names = {"-b", "--branch-of-base-architecture"}, description = "Name of git branch from which this AU was branched. Used to validate changes. Usually 'master'.", required = true)
    String baseBranch;

    @CommandLine.Option(names = {"-t", "--TDDs"}, description = "Run validation for TDDs only")
    boolean tddValidation;

    @CommandLine.Option(names = {"-s", "--stories"}, description = "Run validation for feature stories only")
    boolean capabilityValidation;

    @Getter
    @Spec
    private CommandSpec spec;

    private final ArchitectureDataStructureObjectMapper architectureDataStructureObjectMapper; 
    private final ArchitectureUpdateObjectMapper architectureUpdateObjectMapper; 
    private final FilesFacade filesFacade; 
    private final GitFacade gitFacade; 

    public AuValidateCommand() {
        this.architectureDataStructureObjectMapper = new ArchitectureDataStructureObjectMapper();
        this.architectureUpdateObjectMapper = new ArchitectureUpdateObjectMapper();
        this.filesFacade = new FilesFacade();
        this.gitFacade = new GitFacade();
    }

    @Override
    public Integer call() {
        final var auBranchArch = loadArchitectureOfCurrentBranch();
        if(auBranchArch.isEmpty()) return 1;

        final var baseBranchArch = loadArchitectureOfBranch(baseBranch);
        if(baseBranchArch.isEmpty()) return 1;

        final var au = loadAu();
        if(au.isEmpty()) return 1;

        final ValidationResult validationResults = ArchitectureUpdateValidator.validate(
                au.get(), auBranchArch.get(), baseBranchArch.get()
        );

        final List<ValidationStage> stages = determineValidationStages(tddValidation, capabilityValidation);
        final boolean areAllStagesValid = stages.stream().allMatch(validationResults::isValid);

        if (!areAllStagesValid) {
            final List<ValidationError> errors = getErrorsOfStages(stages, validationResults);
            final String resultToDisplay = getPrettyStringOfErrors(errors);
            spec.commandLine().getErr().println(resultToDisplay);
            return 1;
        } else {
            spec.commandLine().getOut().println("Success, no errors found.");
        }
        return 0;
    }

    private Optional<ArchitectureDataStructure> loadArchitectureOfCurrentBranch() {
        final var productArchitecturePath = productArchitectureDirectory
            .toPath()
            .resolve("product-architecture.yml");

        try {
            return Optional.of(
                architectureDataStructureObjectMapper.readValue(
                    filesFacade.readString(productArchitecturePath)
                )
            );
        } catch (final Exception e) {
            printError("Unable to load architecture file", e);
            return Optional.empty();
        }
    }

    private Optional<ArchitectureUpdate> loadAu() {
        // TODO [ENHANCEMENT]: Use JSON schema validation
        try {
            return Optional.of(
                architectureUpdateObjectMapper.readValue(
                    filesFacade.readString(architectureUpdateFilePath.toPath())
                )
            );
        } catch (final Exception e) {
            printError("Unable to load architecture update file", e);
            return Optional.empty();
        }
    }

    private Optional<ArchitectureDataStructure> loadArchitectureOfBranch(String branch) {
        final var productArchitecturePath = productArchitectureDirectory
            .toPath()
            .resolve("product-architecture.yml");
        try {
            return Optional.of(
                new GitBranchReader(gitFacade, architectureDataStructureObjectMapper)
                    .load(branch, productArchitecturePath)
            );
        } catch (final Exception e) {
            printError("Unable to load '" + branch + "' branch architecture", e);
            return Optional.empty();
        }
    }

    private String getPrettyStringOfErrors(final List<ValidationError> errors) {
        return getTypes(errors).stream()
                .map(type -> getErrorsOfType(type, errors))
                .map(this::getPrettyStringOfErrorsInSingleType)
                .collect(Collectors.joining())
                .trim();
    }

    private List<ValidationError> getErrorsOfStages(final List<ValidationStage> stages, final ValidationResult validationResults) {
        return stages.stream()
                .map(validationResults::getErrors)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<ValidationErrorType> getTypes(final List<ValidationError> errors) {
        return errors.stream()
                .map(ValidationError::getValidationErrorType)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<ValidationError> getErrorsOfType(final ValidationErrorType type, final List<ValidationError> allErrors) {
        return allErrors.stream()
                .filter(error -> error.getValidationErrorType() == type)
                .collect(Collectors.toList());
    }

    private String getPrettyStringOfErrorsInSingleType(final List<ValidationError> errors) {
        return errors.get(0).getValidationErrorType() +
                ":" +
                errors.stream().map(error -> "\n    " + error.getDescription()).collect(Collectors.joining()) +
                "\n";
    }

    private List<ValidationStage> determineValidationStages(final boolean tddValidation, final boolean capabilityValidation) {
        if (tddValidation && capabilityValidation) {
            return List.of(ValidationStage.TDD, ValidationStage.STORY);
        }
        if (tddValidation) {
            return List.of(ValidationStage.TDD);
        }
        if (capabilityValidation) {
            return List.of(ValidationStage.STORY);
        }
        return List.of(ValidationStage.values());
    }

}
