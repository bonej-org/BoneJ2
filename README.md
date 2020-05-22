# BoneJ
BoneJ is a collection of Fiji/ImageJ plug-ins for skeletal biology. It provides free, open source tools for trabecular geometry and whole bone shape analysis. This repository hosts the modern incarnation of BoneJ in development. For legacy ImageJ1 plug-ins, which are no longer maintained, please visit http://bonej.org/legacy.

[![Build Status](https://travis-ci.org/bonej-org/BoneJ2.svg?branch=master)](https://travis-ci.org/bonej-org/BoneJ2)

## Overview
The code is in two main modules: `Legacy` and `Modern`. The code in `Legacy` is originally from [BoneJ1](http://bonej.org/legacy), but it has been refactored to manage its dependencies via Maven. Unlike BoneJ1, the plug-ins in `Legacy` work with Java 8 and the latest version of [Fiji](http://imagej.net/Fiji). However some of them still depend on [3D_Viewer](https://github.com/fiji/3D_Viewer), which is known to have issues with the latest versions of MacOS (see the [forum](https://forum.image.sc)). 

The main development happens in the `Modern` module. It hosts the modernized versions of BoneJ plug-ins, which fully comply with the current ImageJ API. Our goal is that as code matures, `Modern` hosts only "thin" wrapper plug-ins. They should be responsible only for interacting with the user, and collecting and displaying results. The wrappers call algorithms from the [Ops framework](http://imagej.net/ImageJ_Ops), and utility code from `imagej-common`, `scifio` and other such core libraries.

## Links
* [BoneJ wiki](https://github.com/bonej-org/BoneJ2/wiki) for technical documentation
* [ImageJ wiki](http://imagej.net/BoneJ) for BoneJ user documentation
* [ImageJ forum](https://forum.image.sc/tags/bonej) for questions

## Contributing to BoneJ
If you'd like to improve the code in BoneJ or add new features, we'd greatly appreciate it! To get started have a look at the [contribution guidelines](https://github.com/bonej-org/BoneJ2/blob/master/CONTRIBUTING.md). The wiki and forum are good places to find info on how to develop ImageJ based software. 
