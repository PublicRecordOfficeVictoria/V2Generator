# V2Generator

## What is VERS?

This package is part of the Victorian Electronic Records Strategy (VERS)
software release. For more information about VERS see
[here](https://prov.vic.gov.au/recordkeeping-government/vers).

## What is V2Generator?

V2Generator generates VERS Version 2 (VERS V2) VERS Encapsulated Objects (VEOs).

V2Generator has two components:
- V2Generator itself is an API that constructs a VEO using a set of XML templates and datafiles
- V2Creator that wraps V2Generator to call the API driven by a control file.

Version 2 VEOs are specified in PROS 99/007. This specification is now obsolete
and you should use VERS V3. The equivalent code can be found in the neoVEO
package.

## Using V2Generator

V2Creator is run from the command line. 'v2creator -help' will print a
precis of the command line options. The package contains a BAT file.

To use this package you also need to download the VERSCommon package, and this
must be placed in the same directory as the V2Generator package.

Structurally, the package is an Apache Netbeans project.
