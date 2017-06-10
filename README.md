JDT Core + Handly
=================

This is an experimental fork of [Eclipse JDT Core][1] (Neon.0) based on
[Eclipse Handly][2].

The sole purpose of this work is to test Handly in the context of a
non-trivial existing model implementation to ensure that the framework
can, indeed, deliver a solid foundation without affecting any of the
public APIs and most of the internal (non-)APIs of the existing model.

This repository is just an informal playground; it has no relation to
official Eclipse projects.

Setting up developer tools and workspace
----------------------------------------

Download release builds of Eclipse SDK [4.6][3] (Neon.0) and [4.5.2][4]
(Mars.2) for your platform.

Install Eclipse SDK 4.6 and use the `tools.p2f` file provided in the
root of this repository to complete installation of the necessary
development tools (`File` -> `Import...` ->
`Install Software Items from File`).

Import all projects from this Git repository (`File` -> `Import...` ->
`Projects from Git`, don't search for nested projects). Additionally,
the `org.eclipse.jdt.core.tests.binaries` project needs to be checked out
from `git://git.eclipse.org/gitroot/jdt/eclipse.jdt.core.binaries.git`.

Get a 1.8 JDK and set it up in the "Installed JREs" section in the
Eclipse preferences under "Java". Install Eclipse SDK 4.5.2 and specify
it as the default baseline in the "API Baselines" section under "Plug-in
Development".

Testing
-------

To launch an existing test, open it in the editor, click on "Run
Configurations..." in the coolbar, and create a new "JUnit Plug-in
Test". Go to the "Main" tab and choose "Run an application" and select
"[No Application - Headless Mode]".

To launch all the Java model tests at once, run
`org.eclipse.jdt.core.tests.RunModelTests`.

License
-------

[Eclipse Public License (EPL) v1.0][5]

[1]: http://wiki.eclipse.org/JDT_Core
[2]: http://www.eclipse.org/handly
[3]: http://archive.eclipse.org/eclipse/downloads/drops4/R-4.6-201606061100/
[4]: http://archive.eclipse.org/eclipse/downloads/drops4/R-4.5.2-201602121500/
[5]: http://wiki.eclipse.org/EPL
