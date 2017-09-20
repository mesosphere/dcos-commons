# Purpose
This repo hosts the CNI plugins specific to DC/OS. A brief description
of each of the plugins is given below:

* [plugins/l4lb](plugins/l4lb/README.md): A CNI plugin which allows containers in isolated virtual networks to use services provided by [Minuteman](https://github.com/dcos/minuteman) and [Spartan](https://github.com/dcos/spartan).

# Pre-requisites
* GoLang 1.6+
* [Glide](https://github.com/Masterminds/glide)

# Build instructions
The project uses `glide` to capture all the golang dependencies into the vendor folder. In order to build the project just do
```
make
```
To do a clean build run:
```
make clean
```
The build installs the plugins in the `bin` folder.

