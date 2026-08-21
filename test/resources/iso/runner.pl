% Runner for the ISO conformance suite.
%
% A case is  iso(Section, Goal, Expected)  with Expected one of:
%
%   success            the goal has at least one solution
%   failure            the goal has no solution
%   error(Formal)      the goal throws error(Formal, _)
%   ball(Term)         the goal throws Term, which need not be an error/2
%
% Value checks go inside the goal itself, with ==/2 rather than by comparing
% bindings from outside:
%
%   iso('9.1.7', (X is 7 // 2, X == 3), success).
%
% That is less faithful to the inriasuite format, which reports bindings, but
% it needs no variable-name matching in the harness and each case reads as the
% claim it is making.

run_suite(Passed, Failed) :-
	findall(s(S, G, E), iso(S, G, E), Cases),
	run_cases(Cases, 0, Passed, 0, Failed).

run_cases([], P, P, F, F).
run_cases([Case|Cases], P0, P, F0, F) :-
	(   run_case(Case)
	->  P1 is P0 + 1, F1 = F0
	;   P1 = P0, F1 is F0 + 1,
	    report_failure(Case)
	),
	run_cases(Cases, P1, P, F1, F).

run_case(s(_, Goal, success)) :-
	!,
	catch(call(Goal), _, fail).

run_case(s(_, Goal, failure)) :-
	!,
	\+ catch(call(Goal), _, fail).

run_case(s(_, Goal, ball(Expected))) :-
	!,
	catch(( call(Goal), fail ), Ball, true),
	nonvar(Ball),
	Ball == Expected.

run_case(s(_, Goal, error(Formal))) :-
	!,
	catch(( call(Goal), fail ), Ball, true),
	nonvar(Ball),
	Ball = error(Actual, _),
	subsumes_error(Formal, Actual).

% The formal part of the error is matched structurally, so a case can ask for
% type_error(integer, _) without pinning the culprit term.
subsumes_error(Formal, Actual) :-
	copy_term(Formal, F),
	F = Actual.

report_failure(s(Section, Goal, Expected)) :-
	write('FAIL '), write(Section), write('  '),
	writeq(Goal), write('  expected '), writeq(Expected),
	write('  got '), describe(Goal), nl.

describe(Goal) :-
	catch(( call(Goal) -> write(success) ; write(failure) ),
	      Ball,
	      ( write('thrown '), writeq(Ball) )).
