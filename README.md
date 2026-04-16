# Rinne IDEA Toolkit

[English](README.md) | [简体中文](README.zh-CN.md)

IntelliJ IDEA plugin for exploring Java call graphs, sequence diagrams, and MyBatis database mappings directly inside the IDE.

![Method Call Graph Placeholder](docs/images/main-graph-view.png)

## Overview

Rinne IDEA Toolkit helps you inspect static call relationships from a selected Java class without leaving IntelliJ IDEA.

The plugin is built for Java projects and works especially well in layered Spring-style codebases such as:

- `Controller -> Service -> Mapper`
- helper/util chains inside the same class
- MyBatis Mapper XML or annotation SQL mappings

<!-- Plugin description -->
Rinne IDEA Toolkit is an IntelliJ IDEA plugin for visualizing Java method call graphs and sequence diagrams.

It can analyze a selected Java class, display method-level and class-level call relationships in a dedicated tool window, and help you trace callers, callees, full chains, MyBatis table mappings, table fields, and field-level SQL actions.

Key capabilities include:

- method call graph visualization grouped by class
- sequence analysis opened from method context menus
- caller/callee/full-chain focus modes
- MyBatis Mapper to table, field, and field-action visualization
- image export and clipboard copy
- configurable display options, colors, and toolbar switches
- English and Simplified Chinese UI following the IDE language automatically
<!-- Plugin description end -->

## Version 1 Highlights

- Analyze the currently selected Java class from the editor or project view
- Render a layered method call graph grouped by class
- Highlight or filter callers, callees, current path, and full chain
- Show optional unreached methods and calls from unreached methods
- Toggle getter/setter methods and private methods
- Open method-level sequence diagrams in new tabs inside the same tool window
- Export or copy both call graphs and sequence diagrams as images
- Display MyBatis `Mapper -> Table -> Field` mappings
- Expand database fields into finer SQL actions such as `select`, `where`, `update`, `join`, `group by`, `order by`, and `having`
- Customize toolbar visibility, graph options, and light/dark color palettes

## Main Features

### 1. Method Call Graph

- Analyze all declared methods in the selected Java class as graph roots
- Recursively expand project-internal Java calls
- Group nodes by class for better readability
- Navigate to source by double-clicking method nodes
- Support click highlight, right-click path actions, zoom, wheel scroll, and canvas drag

### 2. Focus Modes

- Highlight This Path
- Show Only This Path
- Highlight Full Chain
- Show Only Full Chain
- Highlight Callers Path
- Show Callers Path
- Highlight Callees Path
- Show Callees Path

These modes work for methods, classes, database tables, and database fields.

### 3. Sequence Analysis

- Available from method right-click menus
- Opens in a new tab instead of replacing the call graph
- Builds upstream + downstream static sequence scenarios
- Splits multiple upstream entry chains into separate scenarios
- Supports export, copy, zoom, and settings

### 4. MyBatis Database Mapping

The plugin can statically extend Mapper methods into database structures:

- `Mapper Method -> Table`
- `Mapper Method -> Field`
- expanded `Field -> SQL Action`

Supported sources:

- MyBatis XML Mapper
- MyBatis annotation SQL

Currently supported action categories:

- `select`
- `insert`
- `update`
- `where`
- `join`
- `group by`
- `order by`
- `having`

### 5. Settings and Customization

- Graph options tab
- Toolbar visibility tab
- Color settings tab
- Separate light/dark theme colors
- Graph and sequence settings persisted between sessions

## How To Use

### Analyze a Class

1. Select a Java class in the editor or project tree.
2. Run `Analyze Method Calls`.
3. Open or focus the `Method Call Graph` tool window.
4. Explore the graph and use right-click menus or node buttons for path actions.

### Open a Sequence Diagram

1. In the call graph, right-click a method node.
2. Choose `Sequence Analysis`.
3. A new sequence tab opens in the same tool window.

### Inspect Mapper Tables and Fields

1. Enable `Show Mapper Tables`.
2. Follow Mapper methods to tables and fields on the right side of the graph.
3. Right-click a table field to expand field actions.
4. Right-click a table to expand or collapse all currently visible field actions.

## Screenshots

### Method Call Graph

![Method Call Graph Placeholder](docs/images/main-graph-view.png)

Suggested content:
- controller/service/mapper layered view
- graph toolbar
- grouped classes and method nodes

### Focus and Highlight

![Focus Placeholder](docs/images/path-focus.png)

Suggested content:
- right-click focus menu
- click highlight effect
- full-chain or caller/callee filtering

### Sequence Analysis

![Sequence Placeholder](docs/images/sequence-analysis.png)

Suggested content:
- scenario sections
- participant lifelines
- sequence toolbar

### Mapper Table and Field Mapping

![Mapper Table Placeholder](docs/images/mapper-table-fields.png)

Suggested content:
- mapper to table expansion
- fields inside the table card
- expanded field actions such as `select` or `where`

### Settings Dialog

![Settings Placeholder](docs/images/settings-dialog.png)

Suggested content:
- switches tab
- toolbar tab
- color tab with light/dark settings

## Current Scope

Included in v1:

- Java source analysis
- PSI-based static traversal
- project-internal call graph traversal
- MyBatis XML and annotation SQL parsing
- call graph and sequence diagram visualization

Not included in v1:

- Kotlin mixed-call analysis
- runtime proxy or AOP resolution
- reflection-based execution tracing
- runtime SQL reconstruction
- real database metadata access

## Installation

### From Disk

1. Build the plugin ZIP with Gradle.
2. Open IntelliJ IDEA.
3. Go to `Settings/Preferences -> Plugins`.
4. Click the gear icon.
5. Choose `Install Plugin from Disk...`.
6. Select the generated ZIP file.

### Development

Common local tasks:

```bash
./gradlew build
./gradlew runIde
./gradlew test
```

## Project Structure

```text
src/main/kotlin/.../actions         IDE actions and entry points
src/main/kotlin/.../services        PSI analysis, graph building, preference services
src/main/kotlin/.../toolWindow      Swing UI, graph rendering, sequence rendering
src/main/kotlin/.../model           Graph, sequence, and preference models
src/main/resources/messages         i18n bundles
src/test/kotlin/...                 Plugin tests
docs/images/                        README screenshot placeholders
```

## Release Notes For v1

- First public version
- Java-only static analysis
- Built-in call graph and sequence diagram tool window
- MyBatis table, field, and field-action visualization
- English / Simplified Chinese support following IDEA language
