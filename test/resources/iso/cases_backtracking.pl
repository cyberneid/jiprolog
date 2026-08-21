% Backtracking: ordine e numero delle soluzioni, e ripristino dei legami.
%
% Il numero e l'ordine si verificano con findall dentro il goal, cosi' un caso
% dice esattamente quali soluzioni si aspetta invece di limitarsi a "riesce".

:- multifile(iso/3).

bt_p(1).
bt_p(2).
bt_p(3).

bt_q(a, 1).
bt_q(a, 2).
bt_q(b, 3).

bt_deep(X) :- bt_p(A), bt_p(B), X is A * 10 + B.

% --- ordine e numero
iso('7.8.4', (findall(X, bt_p(X), L), L == [1, 2, 3]),                    success).
iso('7.8.4', (findall(X-Y, bt_q(X, Y), L), L == [a-1, a-2, b-3]),         success).
iso('7.8.4', (findall(X, (bt_p(X), X > 1), L), L == [2, 3]),              success).
iso('7.8.4', (findall(X, bt_deep(X), L), length(L, N), N == 9),           success).
iso('7.8.4', (findall(X, bt_deep(X), L), L == [11,12,13,21,22,23,31,32,33]), success).
iso('7.8.4', (findall(X, (X = 1 ; X = 2 ; X = 3), L), L == [1, 2, 3]),    success).
iso('7.8.4', (findall(X, (bt_p(X), fail), L), L == []),                   success).

% --- i legami vanno ripristinati dopo ogni fallimento
iso('7.8.4', (( bt_p(X), fail ; true ), var(X)),                          success).
iso('7.8.4', (( bt_p(X), X > 5 ; true ), var(X)),                         success).
iso('7.8.4', (( member(X, [a,b,c]), fail ; true ), var(X)),               success).
iso('7.8.4', (( bt_deep(X), fail ; true ), var(X)),                       success).
iso('7.8.4', (( bt_q(X, Y), fail ; true ), var(X), var(Y)),               success).

% --- ripristino attraverso una struttura, non solo una variabile nuda
iso('7.8.4', (( T = f(X), bt_p(X), fail ; true ), var(T)),                success).
iso('7.8.4', (( f(X, Y) = f(1, Z), bt_p(Z), fail ; true ), var(X), var(Y)),  success).

% --- il valore corrente e' quello della soluzione corrente, non di quella prima
iso('7.8.4', (findall(X, (bt_p(X), X mod 2 =:= 1), L), L == [1, 3]),      success).

% --- backtracking dentro i built-in che hanno punti di scelta
iso('8.16.2', (findall(A-B, atom_concat(A, B, abc), L), length(L, N), N == 4),  success).
iso('8.16.3', (findall(S, sub_atom(abc, _, 1, _, S), L), L == [a, b, c]),  success).
iso('8.8.1',  (findall(N, between(1, 5, N), L), L == [1,2,3,4,5]),        success).
iso('8.8.1',  (( between(1, 3, N), N > 5 ; true ), var(N)),               success).

% --- un built-in che lega e poi fallisce non deve lasciare il legame
iso('8.16.3', (\+ sub_atom(abc, B, 1, _, zz), var(B)),                    success).
iso('8.16.2', (\+ atom_concat(A, B, abc), var(A), var(B)),                failure).
iso('9.1.3',  (\+ (X is 1 + 1, X == 3), var(X)),                          success).

% integer_bounds/2 unifica i due argomenti in and: con un secondo argomento
% sbagliato il primo e' gia' stato legato quando il built-in fallisce. E' il
% caso che distingue un built-in che disfa i propri legami da uno che li
% lascia in giro.
iso('8.2.1',  (\+ integer_bounds(X, 999), var(X)),                        success).
iso('8.2.1',  (\+ functor(foo(a), F, 9), var(F)),                         success).
iso('8.2.1',  (\+ foo(a,b) =.. [foo, Y, c], var(Y)),                      success).

% --- backtracking annidato
iso('7.8.4', (findall(L, (findall(X, bt_p(X), L)), Ls), Ls == [[1,2,3]]), success).
iso('7.8.4', (findall(X-L, (bt_p(X), findall(Y, bt_p(Y), L)), R),
              R == [1-[1,2,3], 2-[1,2,3], 3-[1,2,3]]),                    success).

% --- fallimento profondo: la ricorsione deve srotolarsi del tutto
bt_count(0, []) :- !.
bt_count(N, [N|T]) :- N > 0, N1 is N - 1, bt_count(N1, T).

iso('7.8.4', (bt_count(200, L), length(L, N), N == 200),                  success).
iso('7.8.4', (( bt_count(200, L), fail ; true ), var(L)),                 success).

% --- ripetizione: la stessa soluzione riottenuta dopo il backtracking
iso('7.8.4', (findall(X, (bt_p(X), bt_p(_)), L), length(L, N), N == 9),   success).
