# liquibase-extended-cli

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

The following is an example for Windows:

```shell
java -cp "C:/dev/target/liquibase-extended-cli.jar;C:/path/to/picocli-4.7.5.jar;C:/path/to/liquibase-core-4.24.0.jar;C:/path/to/gson-2.10.1.jar" de.adito.context.ContextResolver <absolute file path>
```

### Commands

#### context

Takes an absolute file path and gets all contexts from it.

* Example Input: `C:\dev\project\.liquibase\Data\changelog.xml`
* Example output: `["example", "workspace"]`. Every successful output is written to stdout.
  This will be always a valid JSON array. This array is already sorted.

