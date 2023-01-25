# OpenAI Scala Client - Guice [![version](https://img.shields.io/badge/version-0.0.1-green.svg)](https://cequence.io) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT)

This module provides dependency injection support (by `Guice` library) for the OpenAI Scala client.
The full project documentation can be found [here](../README.md).

## Installation

The currently supported Scala versions are **2.12** and **2.13**.

To pull the library you have to add the following dependency to your *build.sbt*

```
"io.cequence" %% "openai-scala-guice" % "0.0.1"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>io.cequence</groupId>
    <artifactId>openai-scala-guice_2.12</artifactId>
    <version>0.0.1</version>
</dependency>
```