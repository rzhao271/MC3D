# Magic Cube 3D

This repository is a reupload of David Vanderschel's Magic Cube 3D (MC3D)
program with a fix to get it working on more recent versions of Java.

The original source code is available on the 
[archived project page](https://web.archive.org/web/20180725112611/http://david-v.home.texas.net/MC3D/),
and it's also available by viewing the initial commit for this repository.

## Fixes and enhancements

* Fixed a null pointer exception that shows up when running the compiled
  program with Java 11 or newer.
* Fixed some -Xlint:deprecation and -Xlint:unchecked warnings that appear
  when compiling with Java 11 or newer.
* Added a Makefile so that one can compile and package the project from the
  command line.

## Notes

I learned it's best to compile this code with an older version of Java,
but to verify with a newer version of Java:

* Newer versions of Java can run code compiled by older versions of Java,
  but not vice versa. 
* Newer versions of Java might run the older Java code differently than
  older versions of Java. In this case, the program was originally written for
  Java 4, and a new bug appeared when running the program using Java 11.
  At the time of this upload, users are likely on at least Java 11,
  which explains why the original jar file didn't seem to work anymore.

## License

The code is licensed under the GPLv2 with David Vanderschel as the author.
The copyright statements date back to 2005-2006.
