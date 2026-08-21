% Times every bench/3 and prints one "name<TAB>millis<TAB>reps" line each, so
% the output can be diffed between builds.

run_all :-
	( bench(Name, Goal, Reps),
	  time_it(Name, Goal, Reps),
	  fail
	; true
	).

time_it(Name, Goal, Reps) :-
	repeat_goal(Goal, 3),                       % warm the JIT
	T0 is cputime,
	repeat_goal(Goal, Reps),
	T1 is cputime,
	D is T1 - T0,
	write(Name), write('\t'), write(D), write('\t'), write(Reps), nl.

repeat_goal(_, 0) :- !.
repeat_goal(Goal, N) :-
	( call(Goal) -> true ; true ),
	N1 is N - 1,
	repeat_goal(Goal, N1).
