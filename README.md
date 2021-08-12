
[![Build Status](https://travis-ci.org/bonej-org/BoneJ2.svg?branch=master)](https://travis-ci.org/bonej-org/BoneJ2)
# BoneJ
BoneJ is a collection of Fiji/ImageJ plug-ins for skeletal biology. It provides free, open source tools for trabecular geometry and whole bone shape analysis. This repository hosts the modern incarnation of BoneJ in development. If you use BoneJ in your work please cite:

> Domander R, Felder AA, Doube M. 2021 BoneJ2 - refactoring established research software. Wellcome Open Res. **6**.
> doi:[10.12688/wellcomeopenres.16619.1](https://doi.org/10.12688/wellcomeopenres.16619.1)

## Links
* [User guide](https://imagej.net/plugins/bonej)
* [ImageJ forum](https://forum.image.sc/tags/bonej)
* [Developer documentation](https://github.com/bonej-org/BoneJ2/wiki)

For legacy ImageJ1 plug-ins, which are no longer maintained, please visit https://bonej.org/legacy.

## Overview
The code is in two main modules: `Legacy` and `Modern`. The code in `Legacy` is originally from [BoneJ1](https://bonej.org/legacy), but it has been refactored to manage its dependencies via Maven. Unlike BoneJ1, the plug-ins in `Legacy` work with Java 8 and the latest version of [Fiji](https://imagej.github.io/fiji/). However some of them still depend on [3D_Viewer](https://github.com/fiji/3D_Viewer), which is known to have issues with the latest versions of MacOS (see the [forum](https://forum.image.sc)). 

The main development happens in the `Modern` module. It hosts the modernized versions of BoneJ plug-ins, which fully comply with the current ImageJ API. Our goal is that as code matures, `Modern` hosts only "thin" wrapper plug-ins. They should be responsible only for interacting with the user, and collecting and displaying results. The wrappers call algorithms from the [Ops framework](https://imagej.net/ImageJ_Ops), specialised algorithms from BoneJ's own `Ops`, and utility code from `imagej-common`, `scifio` and other such core libraries.

## Contributing to BoneJ
If you'd like to improve the code in BoneJ or add new features, we'd greatly appreciate it! To get started have a look at the [contribution guidelines](https://github.com/bonej-org/BoneJ2/blob/master/CONTRIBUTING.md). The wiki and forum are good places to find info on how to develop ImageJ based software. 
