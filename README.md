# liquibase-extended-cli

Extended CLI functionality for liquibase.

## Build

You can build via `mvn clean install`.

Alternatively, you can build via the `installScript.sh`. This will also copy the jar to the desired directory of the extension.

## Updating dependencies

Whenever you are updating dependencies, you need also check the [vscode-liquibase](https://gitlab.adito.de/plattform/designer/vscode-liquibase)
extension if it still works. If it no longer works, then you might want to update the dependencies there as well.

## Usage

### Basic usage

The built jar does not include any dependency. Instead, you need to give the required jars in the classpath argument, separated by the specific
separator of your OS (`;` (semicolon) for windows, `:` (colon) for macOS and Linux).

You need the following JARs in your classpath

- liquibase-core: Executing liquibase
- picocli: Using CLI features
- snakeyaml: Working with JSON/YAML changelogs
- gson: Parsing the output to a valid JSON
- commons-lang3: Utility dependency needed by liquibase
- commons-io: Utility dependency needed by liquibase
- opencsv: Utility dependency needed by liquibase

The following is an example for Windows:

```shell
java -cp "liquibase-extended-cli.jar;picocli-4.7.5.jar;liquibase-core-4.28.0.jar;commons-io-2.16.1.jar;commons-lang3-3.14.0.jar;gson-2.10.1.jar;opencsv-5.9.jar;snakeyaml-2.2.jar" de.adito.LiquibaseExtendedCli <command and arguments that should be called>
```

Please note that you will need to give correct paths for all the jars.

### Commands

Every command can be executed via the following:

````shell
java -cp "[...]" de.adito.LiquibaseExtendedCli <command-name> <parameters>
````

You can always call `--help` for a detailed command help.

#### context

Takes an absolute file path and gets all contexts from the given changelog and their linked changelogs.

Example call: `de.adito.LiquibaseExtendedCli context "C:\dev\project\.liquibase\Data\changelog.xml"`

Example output: `["example", "workspace"]`. Every successful output is written to stdout.
This will be always a valid JSON array. This array is already sorted.

#### convert

Converts a file or a directory to another liquibase format.

Example call: `de.adito.LiquibaseExtendedCli convert --format YAML "C:\dev\project\.liquibase\Data\changelog.xml" "C:\dev\project\.liquibase\yaml"`

This will write directly to the given path.

**NOTE:** You should always check the created files for any errors.