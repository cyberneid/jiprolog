% ISO/IEC 13211-1 sections 8.2 (unification), 8.3 (type testing),
% 8.4 (term comparison) and 8.5 (term creation and decomposition).

% --- 8.2 unification
:- multifile(iso/3).

iso('8.2.1',  1 = 1,                                    success).
iso('8.2.1',  (X = 1, X == 1),                          success).
iso('8.2.1',  (X = Y, X == Y),                          success).
iso('8.2.1',  1 = 2,                                    failure).
iso('8.2.1',  (f(X, b) = f(a, Y), X == a, Y == b),      success).
iso('8.2.1',  f(a) = f(a, b),                           failure).
iso('8.2.1',  1 = 1.0,                                  failure).
iso('8.2.2',  1 \= 2,                                   success).
iso('8.2.2',  1 \= 1,                                   failure).
iso('8.2.4',  unify_with_occurs_check(1, 1),            success).

% --- 8.3 type testing
iso('8.3.1',  var(_),                                   success).
iso('8.3.1',  var(foo),                                 failure).
iso('8.3.2',  atom(foo),                                success).
iso('8.3.2',  atom([]),                                 success).
iso('8.3.2',  atom(f(x)),                               failure).
iso('8.3.2',  atom(1),                                  failure).
iso('8.3.3',  integer(1),                               success).
iso('8.3.3',  integer(1.0),                             failure).
iso('8.3.4',  float(1.0),                               success).
iso('8.3.4',  float(1),                                 failure).
iso('8.3.5',  atomic(foo),                              success).
iso('8.3.5',  atomic(1),                                success).
iso('8.3.5',  atomic(f(x)),                             failure).
iso('8.3.5',  atomic(_),                                failure).
iso('8.3.6',  compound(f(x)),                           success).
iso('8.3.6',  compound([a]),                            success).
iso('8.3.6',  compound(foo),                            failure).
iso('8.3.6',  compound([]),                             failure).
iso('8.3.7',  nonvar(foo),                              success).
iso('8.3.7',  nonvar(_),                                failure).
iso('8.3.8',  number(1),                                success).
iso('8.3.8',  number(1.0),                              success).
iso('8.3.8',  number(foo),                              failure).
iso('8.3.9',  callable(foo),                            success).
iso('8.3.9',  callable(f(x)),                           success).
iso('8.3.9',  callable(1),                              failure).

% --- 8.4 term comparison and standard order
iso('8.4.1',  1 == 1,                                   success).
iso('8.4.1',  1 == 1.0,                                 failure).
iso('8.4.1',  foo \== bar,                              success).
iso('8.4.1',  (X = Y, X == Y),                          success).
iso('8.4.1',  1 @< foo,                                 success).
iso('8.4.1',  foo @< f(x),                              success).
iso('8.4.1',  f(a) @< f(b),                             success).
iso('8.4.1',  f(a) @< g(a),                             success).
iso('8.4.1',  f(a) @< f(a, b),                          success).
iso('8.4.2',  compare(=, 1, 1),                         success).
iso('8.4.2',  compare(<, 1, 2),                         success).
iso('8.4.2',  compare(>, 2, 1),                         success).
iso('8.4.2',  (compare(Order, foo, bar), Order == (>)),  success).
iso('8.4.3',  (sort([c,a,b,a], L), L == [a,b,c]),       success).
iso('8.4.3',  (sort([], L), L == []),                   success).
iso('8.4.3',  sort(_, _),                               error(instantiation_error)).
iso('8.4.4',  (keysort([2-b,1-a], L), L == [1-a,2-b]),  success).
iso('8.4.4',  (keysort([1-b,1-a], L), L == [1-b,1-a]),  success).
iso('8.4.4',  (msort([c,a,b,a], L), L == [a,a,b,c]),    success).

% --- 8.5 term creation and decomposition
iso('8.5.1',  (functor(foo(a,b), N, A), N == foo, A == 2),  success).
iso('8.5.1',  (functor(foo, N, A), N == foo, A == 0),       success).
iso('8.5.1',  (functor(1, N, A), N == 1, A == 0),           success).
iso('8.5.1',  (functor([a], N, A), N == '.', A == 2),       success).
iso('8.5.1',  (functor(T, foo, 2), T = foo(_,_)),           success).
iso('8.5.1',  (functor(T, foo, 0), T == foo),               success).
iso('8.5.1',  functor(_, _, _),                             error(instantiation_error)).
iso('8.5.1',  functor(_, foo, _),                           error(instantiation_error)).
iso('8.5.1',  functor(_, foo, a),                           error(type_error(integer, a))).
iso('8.5.1',  functor(_, _, 1),                             error(instantiation_error)).
iso('8.5.1',  functor(_, foo(a), 1),                        error(type_error(atomic, foo(a)))).
iso('8.5.1',  functor(_, foo, -1),                          error(domain_error(not_less_than_zero, -1))).
iso('8.5.2',  (arg(1, foo(a,b), X), X == a),                success).
iso('8.5.2',  (arg(2, foo(a,b), X), X == b),                success).
iso('8.5.2',  arg(3, foo(a,b), _),                          failure).
iso('8.5.2',  arg(0, foo(a,b), _),                          failure).
iso('8.5.2',  arg(_, _, _),                                 error(instantiation_error)).
iso('8.5.2',  arg(1, foo, _),                               error(type_error(compound, foo))).
iso('8.5.2',  arg(a, foo(a), _),                            error(type_error(integer, a))).
iso('8.5.3',  (foo(a,b) =.. L, L == [foo,a,b]),             success).
iso('8.5.3',  (foo =.. L, L == [foo]),                      success).
iso('8.5.3',  (1 =.. L, L == [1]),                          success).
iso('8.5.3',  ([a] =.. L, L == ['.', a, []]),               success).
iso('8.5.3',  (T =.. [foo,a,b], T == foo(a,b)),             success).
iso('8.5.3',  (T =.. [foo], T == foo),                      success).
iso('8.5.3',  _ =.. _,                                      error(instantiation_error)).
iso('8.5.3',  _ =.. [foo, a | _],                           error(instantiation_error)).
iso('8.5.3',  _ =.. [_, a],                                 error(instantiation_error)).
iso('8.5.3',  _ =.. [foo(a), b],                            error(type_error(atom, foo(a)))).
iso('8.5.3',  _ =.. [foo(a)],                               error(type_error(atomic, foo(a)))).
iso('8.5.3',  _ =.. [],                                     error(domain_error(non_empty_list, []))).
iso('8.5.3',  _ =.. [1, a],                                 error(type_error(atom, 1))).
iso('8.5.4',  (copy_term(f(A,B,A), T), T = f(X,Y,Z), X == Z, X \== Y),  success).
iso('8.5.4',  (copy_term(a, T), T == a),                    success).

% =../2 and functor/3 must agree that a non-empty list is '.'/2
iso('8.5.3',  (X = [a], X =.. L, Y =.. L, Y == X),          success).
iso('8.5.3',  (X = [a,b,c], X =.. L, Y =.. L, Y == X),      success).
iso('8.5.3',  (T =.. ['.', a, []], T == [a]),               success).
