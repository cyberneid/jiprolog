% Bootstraps the release form of the JIProlog kernel and libraries.
%
% JIProlog ships its kernel and libraries twice: as Prolog source
% (jipkernel.txt and *.pl, used when JIPDebugger.debug is true) and as
% Java-serialized terms (*.jip, used otherwise). The .jip form is produced by
% running JIProlog against itself in debug mode -- this file is that step,
% and replaces the CompileLibraries target of the legacy build.xml.
%
% It is driven by the Maven build (see pom.xml, exec-maven-plugin), which
% passes the resource source directory and the destination directory
% (target/classes/...) explicitly, so nothing is written into src/.
%
% Run standalone with:
%   java -cp <classes> com.ugos.jiprolog.JIProlog -debug \
%        -c tools/compile-libraries.pl \
%        -g "compile_libraries('src/com/ugos/jiprolog/resources', 'target/classes/com/ugos/jiprolog/resources')"
%
% Exits 0 on success and 1 on failure, so the build stops on a broken library.
%
% Keep the list below in sync with src/com/ugos/jiprolog/resources/x.pl,
% which is what decides the set actually loaded at startup.

library_source('jipkernel.txt').
library_source('flags.pl').
library_source('list.pl').
library_source('sys.pl').
library_source('xsets.pl').
library_source('setof.pl').
library_source('xio.pl').
library_source('xdb.pl').
library_source('xexception.pl').
library_source('xreflect.pl').
library_source('xsystem.pl').
library_source('xterm.pl').
library_source('xxml.pl').

compile_libraries(SrcDir, DestDir) :-
	(   catch(compile_all(SrcDir, DestDir), Error, (report(Error), fail))
	->  write('JIProlog libraries compiled into '), write(DestDir), nl,
	    halt(0)
	;   write('ERROR: JIProlog library compilation failed'), nl,
	    halt(1)
	).

compile_all(SrcDir, DestDir) :-
	forall(library_source(File), compile_library(SrcDir, DestDir, File)).

compile_library(SrcDir, DestDir, File) :-
	atom_concat(SrcDir, '/', Prefix),
	atom_concat(Prefix, File, Path),
	compile(Path, DestDir),
	write('  compiled '), write(File), nl.

report(Error) :-
	write('ERROR: '), write(Error), nl.
