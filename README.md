# JIProlog

![JIProlog Logo](/logo.png)

JIProlog is a Prolog interpreter, pure Java 100%, cross-platform and Open Source.

JIProlog offers a high degree of compliance with official and de facto Prolog standards. It also supports built-in predicates and other features common to major Prolog systems.

JIProlog enhances the Java platform by adding the power of Prolog language and extends Prolog by adding the Java framework.

JIProlog integrates Prolog and Java languages in a very fascinating way. It allows calling Prolog predicates from Java without dealing with native code (JNI) and allows invoking Java methods from Prolog as they were predicates.

JIProlog supplies a complete API to link Prolog and Java languages from both sides. The API is composed by three parts:
* Java calls Prolog;
* Prolog calls Java;
* Prolog links JDBC databases.

By design, JIProlog is compliant with Web 3.0 and the mobile world.

Born in later 1998 from an idea by Ugo Chirico, JIProlog has been developed by using cutting edge technologies and following the needs of real world applications.

## Building from source

JIProlog builds with Maven and has no runtime dependencies. A JDK 9 or newer is
needed to build; the resulting jar runs on Java 8 and up.

```bash
mvn package
java -jar target/jiprolog-4.1.7.1.jar -c yourprogram.pl -g yourgoal
```

`mvn package` compiles the Java sources, bootstraps the compiled (`.jip`) form
of the Prolog kernel and libraries by running the interpreter against its own
sources, runs the test suite, and produces a runnable jar.

The sources are ISO-8859-1 encoded; the POM sets this, so build through Maven
rather than invoking `javac` directly.

The home of JIProlog is:
[http://www.jiprolog.com](http://www.jiprolog.com)

JIProlog is open source and it is release under [AGPLv 3.0](https://www.gnu.org/licenses/agpl-3.0.html) or under [commercial license](https://github.com/jiprolog/jiprolog/wiki/License).

The source code is available on GitHub:
[https://github.com/jiprolog/](https://github.com/jiprolog/)

See also:

* [JIProlog Forum](http://www.jiprolog.com/forum.aspx)
* [About](http://www.jiprolog.com#about)
* [Contacts](http://www.jiprolog.com#contacts)
