package net.trilogy.arch.commands.architectureUpdate;

import lombok.Getter;
import net.trilogy.arch.adapter.architectureUpdateYaml.ArchitectureUpdateObjectMapper;
import net.trilogy.arch.adapter.architectureYaml.ArchitectureDataStructureObjectMapper;
import net.trilogy.arch.adapter.git.GitInterface;
import net.trilogy.arch.commands.DisplaysErrorMixin;
import net.trilogy.arch.domain.ArchitectureDataStructure;
import net.trilogy.arch.domain.architectureUpdate.ArchitectureUpdate;
import net.trilogy.arch.facade.FilesFacade;
import net.trilogy.arch.validation.architectureUpdate.*;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

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
    private final GitInterface gitInterface;

    public AuValidateCommand(FilesFacade filesFacade, GitInterface gitInterface) {
        this.architectureDataStructureObjectMapper = new ArchitectureDataStructureObjectMapper();
        this.architectureUpdateObjectMapper = new ArchitectureUpdateObjectMapper();
        this.filesFacade = filesFacade;
        this.gitInterface = gitInterface;
    }

    @Override
    public Integer call() {
        final var auBranchArch = loadArchitectureOfCurrentBranch();
        if (auBranchArch.isEmpty()) return 1;

        final var baseBranchArch = loadArchitectureOfBranch(baseBranch);
        if (baseBranchArch.isEmpty()) return 1;

        final var au = loadAu();
        if (au.isEmpty()) return 1;

        final ValidationResult validationResults = ArchitectureUpdateValidator.validate(
                au.get(), auBranchArch.get(), baseBranchArch.get()
        );

        final List<ValidationStage> stages = determineValidationStages(tddValidation, capabilityValidation);
        final boolean areAllStagesValid = stages.stream().allMatch(validationResults::isValid);

        if (!areAllStagesValid) {
            final List<ValidationError> errors = getErrorsOfStages(stages, validationResults);
            final String resultToDisplay = getPrettyStringOfErrors(errors, baseBranch);
            spec.commandLine().getErr().println(resultToDisplay);
            return 1;
        }

        spec.commandLine().getOut().println("Success, no errors found.");
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
                    gitInterface.load(branch, productArchitecturePath)
            );
        } catch (final Exception e) {
            printError("Unable to load '" + branch + "' branch architecture", e);
            return Optional.empty();
        }
    }

    private String getPrettyStringOfErrors(final List<ValidationError> errors, final String baseBranchName) {
        return getTypes(errors).stream()
                .map(type -> getErrorsOfType(type, errors))
                .map(it -> getPrettyStringOfErrorsInSingleType(it, baseBranchName))
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

    private String getPrettyStringOfErrorsInSingleType(final List<ValidationError> errors, final String baseBranchName) {
        return errors.get(0).getValidationErrorType() + ":" +
                errors.stream()
                        .map(error -> toString(error, baseBranchName))
                        .collect(Collectors.joining()) +
                "\n";
    }

    private String toString(ValidationError error, String baseBranchName) {
        var result = "\n    " + error.getDescription();
        if (error.getValidationErrorType() == ValidationErrorType.INVALID_DELETED_COMPONENT_REFERENCE)
            result += " (Checked architecture in \"" + baseBranchName + "\" branch.)";
        return result;
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
