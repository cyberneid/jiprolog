% Standard Prolog benchmark programs, in the classic set used to compare
% implementations. Each bench/3 fact gives a name, the goal to repeat, and how
% many repetitions, chosen so that every program takes a comparable time.

% ---------------------------------------------------------------- nrev
app([], L, L).
app([H|T], L, [H|R]) :- app(T, L, R).

nrev([], []).
nrev([H|T], R) :- nrev(T, RT), app(RT, [H], R).

range(N, N, [N]) :- !.
range(I, N, [I|T]) :- I < N, I1 is I + 1, range(I1, N, T).

nrev30 :- range(1, 30, L), nrev(L, _).

% ---------------------------------------------------------------- queens
queens(N, Qs) :- numlist_(1, N, Ns), permutation_(Ns, Qs), safe(Qs).

numlist_(N, N, [N]) :- !.
numlist_(I, N, [I|T]) :- I < N, I1 is I + 1, numlist_(I1, N, T).

permutation_([], []).
permutation_(Qs, [Q|Rest]) :- select_(Q, Qs, Qs1), permutation_(Qs1, Rest).

select_(X, [X|T], T).
select_(X, [H|T], [H|R]) :- select_(X, T, R).

safe([]).
safe([Q|Qs]) :- no_attack(Q, Qs, 1), safe(Qs).

no_attack(_, [], _).
no_attack(X, [Y|Ys], N) :-
	X =\= Y + N, X =\= Y - N,
	N1 is N + 1, no_attack(X, Ys, N1).

queens8 :- queens(8, _).

% ---------------------------------------------------------------- deriv
d(U + V, X, DU + DV) :- !, d(U, X, DU), d(V, X, DV).
d(U - V, X, DU - DV) :- !, d(U, X, DU), d(V, X, DV).
d(U * V, X, DU * V + U * DV) :- !, d(U, X, DU), d(V, X, DV).
d(U / V, X, (DU * V - U * DV) / (V * V)) :- !, d(U, X, DU), d(V, X, DV).
d(X, X, 1) :- !.
d(_, _, 0).

deriv :- d(((1 * 2 + 3) * (4 * 5 - 6)) / ((7 + 8) * (9 - 1)), x, _).

% ---------------------------------------------------------------- tak
tak(X, Y, Z, A) :- X =< Y, !, Z = A.
tak(X, Y, Z, A) :-
	X1 is X - 1, Y1 is Y - 1, Z1 is Z - 1,
	tak(X1, Y, Z, A1), tak(Y1, Z, X, A2), tak(Z1, X, Y, A3),
	tak(A1, A2, A3, A).

tak18 :- tak(18, 12, 6, _).

% ---------------------------------------------------------------- fib
fib(0, 0).
fib(1, 1).
fib(N, F) :- N > 1, N1 is N - 1, N2 is N - 2,
             fib(N1, F1), fib(N2, F2), F is F1 + F2.

fib20 :- fib(20, _).

% ---------------------------------------------------------------- database
:- dynamic(item/2).

db_churn :-
	db_fill(1, 200),
	db_lookup(1, 200),
	db_clear.

db_fill(N, Max) :- N > Max, !.
db_fill(N, Max) :- assertz(item(N, N)), N1 is N + 1, db_fill(N1, Max).

db_lookup(N, Max) :- N > Max, !.
db_lookup(N, Max) :- item(N, _), N1 is N + 1, db_lookup(N1, Max).

db_clear :- retractall(item(_, _)).

% ---------------------------------------------------------------- findall
collect :- findall(X-Y, (member(X, [1,2,3,4,5,6,7,8,9,10]),
                         member(Y, [a,b,c,d,e,f,g,h,i,j])), _).

% ---------------------------------------------------------------- atoms
atom_churn :- atom_churn(1, 50).
atom_churn(N, Max) :- N > Max, !.
atom_churn(N, Max) :-
	number_codes(N, Cs), atom_codes(A, Cs),
	atom_concat(pfx_, A, _),
	N1 is N + 1, atom_churn(N1, Max).

% ---------------------------------------------------------------- the set
% Repetition counts are calibrated so each entry runs for roughly a second on
% the baseline build, which keeps a full pass under ten seconds. queens8 and
% tak18 are in the file but out of the set: naive-permutation queens and
% tak(18,12,6) each take minutes at this speed, which makes them useless for
% iterating and fine for a once-off check.
bench(nrev30,     nrev30,      300).
bench(deriv,      deriv,       350).
bench(fib20,      fib20,         1).
bench(db_churn,   db_churn,     20).
bench(findall,    collect,      45).
bench(atom_churn, atom_churn,    5).
