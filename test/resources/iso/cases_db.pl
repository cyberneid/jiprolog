% ISO/IEC 13211-1 sections 8.8 (clause retrieval), 8.9 (clause database)
% and 8.10 (all solutions).

:- multifile(iso/3).

:- dynamic(legs/2).
:- dynamic(insect/1).
:- dynamic(bar/1).

legs(spider, 8).
legs(horse, 4).
insect(bee).
insect(ant).

% --- 8.8 clause retrieval
iso('8.8.1',  (clause(legs(spider, N), true), N == 8),  success).
iso('8.8.1',  clause(legs(_, _), _),                    success).
iso('8.8.1',  clause(_, _),                             error(instantiation_error)).
iso('8.8.1',  clause(4, _),                             error(type_error(callable, 4))).
iso('8.8.1',  clause(legs(_, _), 4),                    error(type_error(callable, 4))).

% --- 8.9 clause database
iso('8.9.1',  (asserta(bar(1)), bar(1)),                success).
iso('8.9.1',  asserta(_),                               error(instantiation_error)).
iso('8.9.1',  asserta(4),                               error(type_error(callable, 4))).
iso('8.9.1',  asserta((foo :- 4)),                      error(type_error(callable, 4))).
iso('8.9.2',  (assertz(bar(2)), bar(2)),                success).
iso('8.9.2',  assertz(_),                               error(instantiation_error)).
iso('8.9.3',  (assertz(bar(3)), retract(bar(3)), \+ bar(3)),  success).
iso('8.9.3',  retract(bar(99)),                         failure).
iso('8.9.3',  retract(_),                               error(instantiation_error)).
iso('8.9.3',  retract(4),                               error(type_error(callable, 4))).

% --- 8.10 all solutions
iso('8.10.1', (findall(X, insect(X), L), L == [bee, ant]),      success).
iso('8.10.1', (findall(_, fail, L), L == []),                   success).
iso('8.10.1', (findall(X, (X = 1 ; X = 2), L), L == [1, 2]),    success).
iso('8.10.1', findall(_, _, _),                                 error(instantiation_error)).
iso('8.10.1', findall(_, 4, _),                                 error(type_error(callable, 4))).
iso('8.10.2', (bagof(X, insect(X), L), L == [bee, ant]),        success).
iso('8.10.2', bagof(_, fail, _),                                failure).
iso('8.10.2', (bagof(X, (X = 1 ; X = 2), L), L == [1, 2]),      success).
iso('8.10.3', (setof(X, insect(X), L), L == [ant, bee]),        success).
iso('8.10.3', setof(_, fail, _),                                failure).
iso('8.10.3', (setof(X, (X = 2 ; X = 1 ; X = 2), L), L == [1, 2]),  success).
iso('8.10.3', (setof(X, member(X, [f(U), f(V)]), L), L == [f(U), f(V)]),  success).
