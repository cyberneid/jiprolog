#!/bin/sh
#
# Runs the INRIA ISO conformance suite against the built jar.
#
# The suite is NOT vendored into this repository: it is third-party material
# from 1999 with no stated licence, and this tree is AGPL. The script fetches
# it into target/, which is gitignored, and leaves it there for re-runs.
#
#   Suite:  P. Deransart, A. Ed-Dbali, L. Cervoni (specification)
#           J.P.E. Hodgson (driver), version 0.9, 1999
#   Source: https://www.deransart.fr/prolog/suites.html
#
# It is deliberately not part of `mvn verify`: it needs the network, it is not
# green (see CODE_REVIEW.md section 21), and a build must not depend on a
# 27-year-old tarball staying reachable.
#
# Usage:  mvn package && tools/run-inriasuite.sh
#
set -eu

JAR=target/jiprolog-4.1.7.1.jar
DIR=target/inriasuite
URL=https://pauillac.inria.fr/~deransar/prolog/inriasuite.tar.gz

[ -f "$JAR" ] || { echo "$JAR is missing - run 'mvn package' first" >&2; exit 1; }

if [ ! -f "$DIR/inriasuite.pl" ]; then
    echo "fetching the suite into $DIR"
    mkdir -p "$DIR"
    curl -sSL --max-time 120 "$URL" | tar xz -C "$DIR" --strip-components=1
fi

JAR_ABS=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")

# run_all_tests opens the case files by bare name, so it has to run from the
# suite directory
cd "$DIR"
java -jar "$JAR_ABS" -c inriasuite.pl -g "run_all_tests, halt" 2>&1 |
    grep -vE '^\*\*|^$|^consulting|^running goal'
