# BoneJ contribution guidelines

## Prerequisites
* Familiarize yourself with GitHub
* [Java 8](https://openjdk.java.net/) (newer version is fine, but the project still uses 1.8 bytecode)
* [Maven](https://imagej.github.io/develop/maven)
* [GitHub](https://imagej.github.io/develop/github) and Git
* Preferrably an IDE such as [Eclipse](https://www.eclipse.org), [IntelliJ](https://www.jetbrains.com/idea/) or [NetBeans](https://netbeans.org)
  * Eclipse requires the m2e-egit connector to be installed so that you can seamlessly import your BoneJ2 fork from GitHub, but the version in the marketplace is often too old: you may need to [install it from the repository](https://stackoverflow.com/questions/51359823/m2e-egit-connector-for-scm-on-eclipse-photon-failure).

## Getting started
1) Create a [fork](https://imagej.github.io/develop/improving-the-code)
2) [Synchronize](https://help.github.com/articles/syncing-a-fork/) your fork
3) Create a topic branch for your fix / new feature, e.g. `fix-issue-#1`

## Commits
* Write [descriptive](https://chris.beams.io/posts/git-commit/) commit messages
* Ideally each commit in the history should build
* Keep commits small. For example:
  - `POM: add dependency`
  - `Add myMethod`
  - `Add test`
  - `Add Javadoc`
  - `Format code`

## Creating a pull request (PR)
1) Before creating a PR:
  * Your code should have tests
  * At least `public` API should have [Javadoc](http://drjava.org/docs/user/ch10.html)
    - You can check that your Javadoc is valid by running `mvn javadoc:javadoc`
  * Check that Maven can build BoneJ without errors by running `mvn clean package`
2) Create a PR and wait for a review
3) If team members request changes to your code, add commits until PR is accepted

## Licensing
Code added to BoneJ should be licensed under [BSD-2](https://github.com/bonej-org/BoneJ2/blob/master/LICENCE).

## Finally
Don't let the guidelines discourage you, they are not set in stone. We'll help to get your PR ready!
