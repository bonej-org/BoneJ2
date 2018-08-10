# BoneJ contribution guidelines

## Prerequirements
* Familiarize yourself with GitHub
* [Maven](https://github.com/bonej-org/BoneJ2/wiki/Maven)
* [Git](https://git-scm.com)
* Preferrably an IDE such as [Eclipse](https://www.eclipse.org), [IntelliJ](https://www.jetbrains.com/idea/) or [NetBeans](https://netbeans.org)

## Getting started
1) Create a [fork](http://imagej.net/How_to_contribute_to_an_existing_plugin_or_library)
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
  * Check that [Maven](https://github.com/bonej-org/BoneJ2/wiki/Maven) can build BoneJ by running `mvn clean package`
2) Create a PR and wait for a review
3) If team members request changes to your code, add commits until PR is accepted

## Licensing
Code added to BoneJ should be licensed under [BSD-2](https://github.com/bonej-org/BoneJ2/blob/master/LICENCE).

## Finally
Don't let the guidelines discourage you, they are not set in stone. We'll help to get your PR ready!
