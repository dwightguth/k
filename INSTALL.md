<!-- Copyright (c) 2012-2014 K Team. All Rights Reserved. -->
Here are instructions for installing K from the release zip/tgz archive.
Users who checked out the sources should follow the instructions in src/README.md.

1. Prerequisites:
  * Java Runtime Edition version 7 or higher (http://java.com/en/download/index.jsp)
  * To make sure java is installed properly, call `java -version` in a terminal.

2. Install:
  * Unzip this directory in your preferred location.  For convenient usage,
    update your $PATH with <preferred-location>/k/bin.

3. Test:
  * Go to one of the examples (say k/tutorial/2_languages/1_simple/1_untyped/).
    Assuming k/bin is in your $PATH, you can compile definitions using 
    the `kompile simple-untyped.k` command.
    To execute a program you can use `krun tests/diverse/factorial.simple`.

4. (Optional) Latex:
  * To use the pdf backend, a relatively recent installation 
    of Latex is required.  Besides basic packages, the following (texlive)
    packages are needed: 
  * bera, bezos, bookmark, datetime, ec, etoolbox, fancybox, fancyvrb, import, 
    listings, marginnote, microtype, ms, pdfcomment, pgf (up-to-date), preview, 
    soul, stmaryrd, times, titlesec, ucs, url, xcolor, and xkeyval.

--------------------------------------------------------------------------

We present some instructions for installing TeX Live on Unix-like machines.

Although most linux users prefer using a package manager we strongly advise 
them to install texlive manually as our latex compilation is only known to 
work with a texlive 2010 distribution and the most recent version of the 
pgf package. 

To do that one can use the TeX Live Quick install method 
<http://www.tug.org/texlive/quickinstall.html>.

For Mac OSX users the preferred method is to install MacTex
<http://www.tug.org/mactex/>

A full TeX Live installation should provide all the latex packages mentioned
in the prerequisites. Note however that our LaTeX macros rely on a quite 
recent version of the pgf package. To make sure you have the latest version
of this package you can upgrade it using the TeX Live package manager.
   
    $ tlmgr update pgf 

If using a partial TeX Live installation which does not provide all the 
TeX Live packages specified above, these can be installed with the command:
    
    $ tlmgr install  bera bezos bookmark datetime ec etoolbox fancybox \
                     fancyvrb import listings marginnote microtype ms \
                     pdfcomment pgf preview soul stmaryrd times titlesec ucs \
                     url xcolor xkeyval 